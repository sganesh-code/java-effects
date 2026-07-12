package io.effects.recipes.escalatable;

import io.effects.recipes.escalatable.models.*;
import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.StateRepository;
import io.effects.ports.TelemetryPort;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.ProcessCoordinator;
import io.effects.recipes.Recipe;
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * An Object-Oriented "Recipe" representing an Escalatable Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside EscalationLedger).
 */
public final class EscalatableProcess<ID, T, C> implements Recipe<ID, EscalatableRequest<ID, T, C>> {
    private final StateRepository<ID, EscalationLedger<ID, T, C>> repository;
    private final EventPublisher<EscalatableEvent<ID, T>> publisher;
    private final TelemetryPort telemetry;
    private final ProcessCoordinator<ID, EscalationLedger<ID, T, C>, EscalatableEvent<ID, T>> coordinator;
    private final ConcurrentMap<ID, EscalatableRequest<ID, T, C>> requests = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public EscalatableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public EscalatableProcess(
        StateRepository<ID, EscalationLedger<ID, T, C>> repository,
        EventPublisher<EscalatableEvent<ID, T>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.coordinator = new ProcessCoordinator<>(repository, publisher, telemetry, "escalatable");
    }

    @Override
    public IO<Void> register(ID caseId, EscalatableRequest<ID, T, C> request) {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            requests.put(caseId, request);
            return null;
        });
    }

    @Override
    public IO<Void> unregister(ID caseId) {
        Objects.requireNonNull(caseId);
        return IO.delay(() -> {
            requests.remove(caseId);
            return null;
        });
    }

    @Override
    public IO<Boolean> isRegistered(ID caseId) {
        Objects.requireNonNull(caseId);
        return IO.delay(() -> requests.containsKey(caseId));
    }

    /**
     * Coordinates the filing of a new case at a starting tier.
     */
    public IO<Either<String, EscalationLedger<ID, T, C>>> file(ID caseId, T proposedTier, String handlerId, C comment, Instant now) {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(proposedTier);
        Objects.requireNonNull(handlerId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        EscalatableRequest<ID, T, C> request = requests.get(caseId);
        if (request == null) {
            return IO.of(Either.left("Escalatable request domain object not registered: " + caseId));
        }

        return coordinator.coordinate(
            caseId,
            "file",
            optRecord -> {
                if (optRecord.isPresent() && optRecord.get().isFiled()) {
                    return Either.left("Case has already been filed: " + caseId);
                }
                EscalationLedger<ID, T, C> ledger = optRecord.orElseGet(() -> EscalationLedger.initiate(caseId));
                Either<String, EscalatableEvent<ID, T>> eitherEvent = ledger.file(proposedTier, handlerId, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates triggering an SLA warning.
     */
    public IO<Either<String, EscalationLedger<ID, T, C>>> triggerSLAWarning(ID caseId, Instant deadline, C comment, Instant now) {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(deadline);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        EscalatableRequest<ID, T, C> request = requests.get(caseId);
        if (request == null) {
            return IO.of(Either.left("Escalatable request domain object not registered: " + caseId));
        }

        return coordinator.coordinate(
            caseId,
            "triggerSLAWarning",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Escalation ledger not found: " + caseId);
                }
                EscalationLedger<ID, T, C> ledger = optRecord.get();
                Either<String, EscalatableEvent<ID, T>> eitherEvent = ledger.triggerSLAWarning(deadline, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates escalating a case.
     */
    public IO<Either<String, EscalationLedger<ID, T, C>>> escalate(ID caseId, T proposedTier, C comment, Instant now) {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(proposedTier);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        EscalatableRequest<ID, T, C> request = requests.get(caseId);
        if (request == null) {
            return IO.of(Either.left("Escalatable request domain object not registered: " + caseId));
        }

        return coordinator.coordinate(
            caseId,
            "escalate",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Escalation ledger not found: " + caseId);
                }
                EscalationLedger<ID, T, C> ledger = optRecord.get();
                Either<String, EscalatableEvent<ID, T>> eitherEvent = ledger.escalate(proposedTier, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates de-escalating a case.
     */
    public IO<Either<String, EscalationLedger<ID, T, C>>> deescalate(ID caseId, T proposedTier, C comment, Instant now) {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(proposedTier);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        EscalatableRequest<ID, T, C> request = requests.get(caseId);
        if (request == null) {
            return IO.of(Either.left("Escalatable request domain object not registered: " + caseId));
        }

        return coordinator.coordinate(
            caseId,
            "deescalate",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Escalation ledger not found: " + caseId);
                }
                EscalationLedger<ID, T, C> ledger = optRecord.get();
                Either<String, EscalatableEvent<ID, T>> eitherEvent = ledger.deescalate(proposedTier, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates reassigning a case.
     */
    public IO<Either<String, EscalationLedger<ID, T, C>>> reassign(ID caseId, String proposedHandlerId, C comment, Instant now) {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(proposedHandlerId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        EscalatableRequest<ID, T, C> request = requests.get(caseId);
        if (request == null) {
            return IO.of(Either.left("Escalatable request domain object not registered: " + caseId));
        }

        return coordinator.coordinate(
            caseId,
            "reassign",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Escalation ledger not found: " + caseId);
                }
                EscalationLedger<ID, T, C> ledger = optRecord.get();
                Either<String, EscalatableEvent<ID, T>> eitherEvent = ledger.reassign(proposedHandlerId, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates resolving a case.
     */
    public IO<Either<String, EscalationLedger<ID, T, C>>> resolve(ID caseId, C comment, Instant now) {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        return coordinator.coordinate(
            caseId,
            "resolve",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Escalation ledger not found: " + caseId);
                }
                EscalationLedger<ID, T, C> ledger = optRecord.get();
                Either<String, EscalatableEvent<ID, T>> eitherEvent = ledger.resolve(comment, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }
}
