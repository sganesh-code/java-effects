package io.effects.recipes.approvable;

import io.effects.Either;
import io.effects.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.StateRepository;
import io.effects.ports.TelemetryPort;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.ProcessCoordinator;
import io.effects.recipes.ProcessRegistry;
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * An Object-Oriented "Recipe" representing an Approval Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside ApprovalRecord).
 */
public final class ApprovalProcess<ID, A, C> implements ProcessRegistry<ID, ApprovableRequest<ID, A, C>> {
    private final StateRepository<ID, ApprovalRecord<ID, A, C>> repository;
    private final EventPublisher<ApprovalEvent<ID, A>> publisher;
    private final TelemetryPort telemetry;
    private final ProcessCoordinator<ID, ApprovalRecord<ID, A, C>, ApprovalEvent<ID, A>> coordinator;
    private final ConcurrentMap<ID, ApprovableRequest<ID, A, C>> requests = new ConcurrentHashMap<>();

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
        StateRepository<ID, ApprovalRecord<ID, A, C>> repository,
        EventPublisher<ApprovalEvent<ID, A>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.coordinator = new ProcessCoordinator<>(repository, publisher, telemetry, "approvable");
    }

    /**
     * Registers a behavioral ownable request domain object.
     */
    @Override
    public IO<Void> register(ID requestId, ApprovableRequest<ID, A, C> request) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            requests.put(requestId, request);
            return null;
        });
    }

    @Override
    public IO<Void> unregister(ID requestId) {
        Objects.requireNonNull(requestId);
        return IO.delay(() -> {
            requests.remove(requestId);
            return null;
        });
    }

    @Override
    public IO<Boolean> isRegistered(ID requestId) {
        Objects.requireNonNull(requestId);
        return IO.delay(() -> requests.containsKey(requestId));
    }

    /**
     * Evaluates initial submission and saves/publishes.
     */
    public IO<Either<String, ApprovalRecord<ID, A, C>>> submit(ID requestId, String initiatorId, C submitComment, Instant now) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(initiatorId);
        Objects.requireNonNull(submitComment);
        Objects.requireNonNull(now);

        ApprovableRequest<ID, A, C> request = requests.get(requestId);
        if (request == null) {
            return IO.of(Either.left("Request domain object not registered: " + requestId));
        }

        return coordinator.coordinate(
            requestId,
            "submit",
            optRecord -> {
                if (optRecord.isPresent()) {
                    return Either.left("Request already submitted: " + requestId);
                }
                return ApprovalRecord.submit(requestId, initiatorId, submitComment, request, now);
            },
            Function.identity()
        );
    }

    /**
     * Executes validation and transitions to next approval stage.
     */
    public IO<Either<String, ApprovalRecord<ID, A, C>>> approve(ID requestId, String approverId, A approverRole, C detail, Instant now) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(approverId);
        Objects.requireNonNull(approverRole);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(now);

        ApprovableRequest<ID, A, C> request = requests.get(requestId);
        if (request == null) {
            return IO.of(Either.left("Request domain object not registered: " + requestId));
        }

        return coordinator.coordinate(
            requestId,
            "approve",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Request register not found: " + requestId);
                }
                ApprovalRecord<ID, A, C> record = optRecord.get();
                Either<String, ApprovalEvent<ID, A>> eitherEvent = record.approve(approverId, approverRole, detail, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(record, event));
            },
            Function.identity()
        );
    }

    /**
     * Transitions request to terminal rejected state.
     */
    public IO<Either<String, ApprovalRecord<ID, A, C>>> reject(ID requestId, String approverId, A approverRole, C reason, Instant now) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(approverId);
        Objects.requireNonNull(approverRole);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);

        ApprovableRequest<ID, A, C> request = requests.get(requestId);
        if (request == null) {
            return IO.of(Either.left("Request domain object not registered: " + requestId));
        }

        return coordinator.coordinate(
            requestId,
            "reject",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Request register not found: " + requestId);
                }
                ApprovalRecord<ID, A, C> record = optRecord.get();
                Either<String, ApprovalEvent<ID, A>> eitherEvent = record.reject(approverId, approverRole, reason, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(record, event));
            },
            Function.identity()
        );
    }

    /**
     * Transitions request to escalated state with a new required authority level.
     */
    public IO<Either<String, ApprovalRecord<ID, A, C>>> escalate(ID requestId, String approverId, A approverRole, A targetAuthority, C reason, Instant now) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(approverId);
        Objects.requireNonNull(approverRole);
        Objects.requireNonNull(targetAuthority);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);

        ApprovableRequest<ID, A, C> request = requests.get(requestId);
        if (request == null) {
            return IO.of(Either.left("Request domain object not registered: " + requestId));
        }

        return coordinator.coordinate(
            requestId,
            "escalate",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Request register not found: " + requestId);
                }
                ApprovalRecord<ID, A, C> record = optRecord.get();
                Either<String, ApprovalEvent<ID, A>> eitherEvent = record.escalate(approverId, approverRole, targetAuthority, reason, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(record, event));
            },
            Function.identity()
        );
    }
}
