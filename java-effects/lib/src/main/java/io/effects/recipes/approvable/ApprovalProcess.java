package io.effects.recipes.approvable;

import io.effects.Either;
import io.effects.IO;
import io.effects.ForIO;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An Object-Oriented "Recipe" representing an Approval Process Manager.
 * It coordinates routing messages, evaluating domain invariants, persistence, event emission, and telemetry.
 * 
 * In accordance with our architectural boundary, this process represents the monadic infrastructure
 * engine, and thus exposes purely monadic APIs (returning IO) to allow lazy, virtual-thread execution,
 * cancellation, and pipeline composition.
 */
public final class ApprovalProcess {
    private final ApprovalStateRepository repository;
    private final ApprovalEventPublisher publisher;
    private final ApprovalTelemetryPort telemetry;
    private final ConcurrentMap<String, ApprovableRequest> requests = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public ApprovalProcess() {
        this(new InMemoryApprovalStateRepository(), new InMemoryApprovalEventPublisher(), new NoOpApprovalTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public ApprovalProcess(
        ApprovalStateRepository repository,
        ApprovalEventPublisher publisher,
        ApprovalTelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    /**
     * Registers a behavioral request domain object.
     */
    public IO<Void> register(String requestId, ApprovableRequest request) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            requests.put(requestId, request);
            return null;
        });
    }

    /**
     * Evaluates initial submission and saves/publishes.
     */
    public IO<Either<String, ApprovalRecord>> submit(String requestId, String initiatorId, Instant now) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(initiatorId);
        Objects.requireNonNull(now);

        ApprovableRequest request = requests.get(requestId);
        if (request == null) {
            return IO.of(Either.left("Request domain object not registered: " + requestId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.findRecord(requestId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isPresent()) {
                    return IO.of(Either.<String, ApprovalRecord>left("Request already submitted: " + requestId));
                }

                // Invoke pure synchronous domain trial
                InitialAssessment assessment = request.evaluateInitialSubmission(now);
                ApprovalRecord record = new ApprovalRecord(
                    requestId,
                    initiatorId,
                    Status.PENDING,
                    null
                );

                ApprovalDecision submitDecision = new ApprovalDecision(
                    UUID.randomUUID().toString(),
                    initiatorId,
                    "INITIATOR",
                    DecisionType.APPROVE,
                    "Submitted for approval",
                    now
                );
                record.recordDecision(submitDecision, assessment.initialStatus(), assessment.requiredAuthority());

                ApprovalEvent event;
                if (record.status() == Status.APPROVED) {
                    event = new RequestApproved(requestId, initiatorId, "AUTO_APPROVED", now);
                } else {
                    event = new RequestSubmitted(requestId, initiatorId, record.requiredAuthority(), now);
                }

                return repository.saveRecord(record)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSubmissionSuccess(requestId))
                    .flatMap(v -> telemetry.recordDuration(requestId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, ApprovalRecord>right(record));
            })
            .yield((startTime, optRecord, result) -> result);
    }

    /**
     * Executes validation and transitions to next approval stage.
     */
    public IO<Either<String, ApprovalRecord>> approve(String requestId, String approverId, String approverRole, String comment, Instant now) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(approverId);
        Objects.requireNonNull(approverRole);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        ApprovableRequest request = requests.get(requestId);
        if (request == null) {
            return IO.of(Either.left("Request domain object not registered: " + requestId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.findRecord(requestId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isEmpty()) {
                    return IO.of(Either.<String, ApprovalRecord>left("Request record not found: " + requestId));
                }

                ApprovalRecord record = optRecord.get();
                if (record.status() == Status.APPROVED) {
                    return IO.of(Either.<String, ApprovalRecord>right(record)); // Idempotent success
                }
                if (record.status() == Status.REJECTED) {
                    return IO.of(Either.<String, ApprovalRecord>left("Cannot approve a rejected request: " + requestId));
                }

                // Invoke pure synchronous domain decision evaluation
                Either<String, NextStep> eitherNext = request.evaluateDecision(
                    record, approverId, approverRole, DecisionType.APPROVE, comment, now
                );

                if (eitherNext.isLeft()) {
                    return IO.of(Either.<String, ApprovalRecord>left(eitherNext.getLeft()));
                }

                NextStep next = eitherNext.getRight();
                ApprovalDecision decision = new ApprovalDecision(
                    UUID.randomUUID().toString(),
                    approverId,
                    approverRole,
                    DecisionType.APPROVE,
                    comment,
                    now
                );
                record.recordDecision(decision, next.nextStatus(), next.nextRequiredAuthority());

                ApprovalEvent event;
                if (record.status() == Status.APPROVED) {
                    event = new RequestApproved(requestId, approverId, comment, now);
                } else {
                    event = new RequestSubmitted(requestId, record.initiatorId(), record.requiredAuthority(), now);
                }

                return repository.saveRecord(record)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordApprovalSuccess(requestId))
                    .flatMap(v -> telemetry.recordDuration(requestId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, ApprovalRecord>right(record));
            })
            .yield((startTime, optRecord, result) -> result);
    }

    /**
     * Transitions request to terminal rejected state.
     */
    public IO<Either<String, ApprovalRecord>> reject(String requestId, String approverId, String approverRole, String reason, Instant now) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(approverId);
        Objects.requireNonNull(approverRole);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);

        ApprovableRequest request = requests.get(requestId);
        if (request == null) {
            return IO.of(Either.left("Request domain object not registered: " + requestId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.findRecord(requestId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isEmpty()) {
                    return IO.of(Either.<String, ApprovalRecord>left("Request record not found: " + requestId));
                }

                ApprovalRecord record = optRecord.get();
                if (record.status() == Status.REJECTED) {
                    return IO.of(Either.<String, ApprovalRecord>right(record)); // Idempotent success
                }
                if (record.status() == Status.APPROVED) {
                    return IO.of(Either.<String, ApprovalRecord>left("Cannot reject an already approved request: " + requestId));
                }

                // Invoke pure synchronous domain decision evaluation
                Either<String, NextStep> eitherNext = request.evaluateDecision(
                    record, approverId, approverRole, DecisionType.REJECT, reason, now
                );

                if (eitherNext.isLeft()) {
                    return IO.of(Either.<String, ApprovalRecord>left(eitherNext.getLeft()));
                }

                ApprovalDecision decision = new ApprovalDecision(
                    UUID.randomUUID().toString(),
                    approverId,
                    approverRole,
                    DecisionType.REJECT,
                    reason,
                    now
                );
                record.recordDecision(decision, Status.REJECTED, null);

                ApprovalEvent event = new RequestRejected(requestId, approverId, reason, now);

                return repository.saveRecord(record)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordRejection(requestId, reason))
                    .flatMap(v -> telemetry.recordDuration(requestId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, ApprovalRecord>right(record));
            })
            .yield((startTime, optRecord, result) -> result);
    }

    /**
     * Transitions request to escalated state with a new required authority level.
     */
    public IO<Either<String, ApprovalRecord>> escalate(String requestId, String approverId, String approverRole, String targetAuthority, String reason, Instant now) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(approverId);
        Objects.requireNonNull(approverRole);
        Objects.requireNonNull(targetAuthority);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);

        ApprovableRequest request = requests.get(requestId);
        if (request == null) {
            return IO.of(Either.left("Request domain object not registered: " + requestId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.findRecord(requestId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isEmpty()) {
                    return IO.of(Either.<String, ApprovalRecord>left("Request record not found: " + requestId));
                }

                ApprovalRecord record = optRecord.get();
                if (record.isTerminal()) {
                    return IO.of(Either.<String, ApprovalRecord>left("Cannot escalate a finalized request: " + requestId));
                }

                // Invoke pure synchronous domain decision evaluation
                Either<String, NextStep> eitherNext = request.evaluateDecision(
                    record, approverId, approverRole, DecisionType.ESCALATE, reason, now
                );

                if (eitherNext.isLeft()) {
                    return IO.of(Either.<String, ApprovalRecord>left(eitherNext.getLeft()));
                }

                ApprovalDecision decision = new ApprovalDecision(
                    UUID.randomUUID().toString(),
                    approverId,
                    approverRole,
                    DecisionType.ESCALATE,
                    reason,
                    now
                );
                record.recordDecision(decision, Status.ESCALATED, targetAuthority);

                ApprovalEvent event = new RequestEscalated(requestId, approverId, targetAuthority, reason, now);

                return repository.saveRecord(record)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordEscalation(requestId))
                    .flatMap(v -> telemetry.recordDuration(requestId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, ApprovalRecord>right(record));
            })
            .yield((startTime, optRecord, result) -> result);
    }
}
