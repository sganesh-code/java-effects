package io.effects.recipes.approvable;

import io.effects.Either;
import io.effects.IO;
import io.effects.ForIO;
import io.effects.ports.EventPublisher;
import io.effects.ports.StateRepository;
import io.effects.ports.TelemetryPort;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An Object-Oriented "Recipe" representing an Approval Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside ApprovalRecord).
 */
public final class ApprovalProcess {
    private final StateRepository<String, ApprovalRecord> repository;
    private final EventPublisher<ApprovalEvent> publisher;
    private final TelemetryPort telemetry;
    private final ConcurrentMap<String, ApprovableRequest> requests = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public ApprovalProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public ApprovalProcess(
        StateRepository<String, ApprovalRecord> repository,
        EventPublisher<ApprovalEvent> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    /**
     * Registers a behavioral ownable request domain object.
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
            .bind(startTime -> repository.find(requestId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isPresent()) {
                    return IO.of(Either.<String, ApprovalRecord>left("Request already submitted: " + requestId));
                }

                // Delegate creation and transition to rich aggregate factory
                Either<String, TransitionResult<ApprovalRecord, ApprovalEvent>> submitResult = ApprovalRecord.submit(
                    requestId, initiatorId, request, now
                );

                if (submitResult.isLeft()) {
                    return IO.of(Either.<String, ApprovalRecord>left(submitResult.getLeft()));
                }

                TransitionResult<ApprovalRecord, ApprovalEvent> result = submitResult.getRight();
                ApprovalRecord record = result.aggregate();
                ApprovalEvent event = result.event();

                return repository.save(record.requestId(), record)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("approvable", requestId + ":submit"))
                    .flatMap(v -> telemetry.recordDuration("approvable", requestId, System.currentTimeMillis() - startTime))
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
            .bind(startTime -> repository.find(requestId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isEmpty()) {
                    return IO.of(Either.<String, ApprovalRecord>left("Request record not found: " + requestId));
                }

                ApprovalRecord record = optRecord.get();

                // Delegate execution directly to rich aggregate!
                Either<String, ApprovalEvent> eitherEvent = record.approve(approverId, approverRole, comment, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, ApprovalRecord>left(eitherEvent.getLeft()));
                }

                ApprovalEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(record.requestId(), record)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("approvable", requestId + ":approve"))
                    .flatMap(v -> telemetry.recordDuration("approvable", requestId, System.currentTimeMillis() - startTime))
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
            .bind(startTime -> repository.find(requestId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isEmpty()) {
                    return IO.of(Either.<String, ApprovalRecord>left("Request record not found: " + requestId));
                }

                ApprovalRecord record = optRecord.get();

                // Delegate execution directly to rich aggregate!
                Either<String, ApprovalEvent> eitherEvent = record.reject(approverId, approverRole, reason, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, ApprovalRecord>left(eitherEvent.getLeft()));
                }

                ApprovalEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(record.requestId(), record)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordFailure("approvable", requestId + ":reject", reason))
                    .flatMap(v -> telemetry.recordDuration("approvable", requestId, System.currentTimeMillis() - startTime))
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
            .bind(startTime -> repository.find(requestId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isEmpty()) {
                    return IO.of(Either.<String, ApprovalRecord>left("Request record not found: " + requestId));
                }

                ApprovalRecord record = optRecord.get();

                // Delegate execution directly to rich aggregate!
                Either<String, ApprovalEvent> eitherEvent = record.escalate(approverId, approverRole, targetAuthority, reason, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, ApprovalRecord>left(eitherEvent.getLeft()));
                }

                ApprovalEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(record.requestId(), record)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("approvable", requestId + ":escalate"))
                    .flatMap(v -> telemetry.recordDuration("approvable", requestId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, ApprovalRecord>right(record));
            })
            .yield((startTime, optRecord, result) -> result);
    }
}
