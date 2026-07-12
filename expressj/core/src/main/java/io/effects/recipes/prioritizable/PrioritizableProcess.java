package io.effects.recipes.prioritizable;

import io.effects.recipes.prioritizable.models.*;
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
 * An Object-Oriented "Recipe" representing a Prioritizable Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside PriorityLedger).
 */
public final class PrioritizableProcess<ID, P, C> implements Recipe<ID, PrioritizableRequest<ID, P, C>> {
    private final StateRepository<ID, PriorityLedger<ID, P, C>> repository;
    private final EventPublisher<PrioritizableEvent<ID, P>> publisher;
    private final TelemetryPort telemetry;
    private final ProcessCoordinator<ID, PriorityLedger<ID, P, C>, PrioritizableEvent<ID, P>> coordinator;
    private final ConcurrentMap<ID, PrioritizableRequest<ID, P, C>> requests = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public PrioritizableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public PrioritizableProcess(
        StateRepository<ID, PriorityLedger<ID, P, C>> repository,
        EventPublisher<PrioritizableEvent<ID, P>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.coordinator = new ProcessCoordinator<>(repository, publisher, telemetry, "prioritizable");
    }

    @Override
    public IO<Void> register(ID workId, PrioritizableRequest<ID, P, C> request) {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            requests.put(workId, request);
            return null;
        });
    }

    @Override
    public IO<Void> unregister(ID workId) {
        Objects.requireNonNull(workId);
        return IO.delay(() -> {
            requests.remove(workId);
            return null;
        });
    }

    @Override
    public IO<Boolean> isRegistered(ID workId) {
        Objects.requireNonNull(workId);
        return IO.delay(() -> requests.containsKey(workId));
    }

    /**
     * Coordinates setting the initial work priority level.
     */
    public IO<Either<String, PriorityLedger<ID, P, C>>> sequence(ID workId, P proposedPriority, C comment, Instant now) {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(proposedPriority);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        PrioritizableRequest<ID, P, C> request = requests.get(workId);
        if (request == null) {
            return IO.of(Either.left("Prioritizable request domain object not registered: " + workId));
        }

        return coordinator.coordinate(
            workId,
            "sequence",
            optRecord -> {
                if (optRecord.isPresent() && optRecord.get().hasPriorityBeenSet()) {
                    return Either.left("Priority has already been sequenced on work: " + workId);
                }
                PriorityLedger<ID, P, C> ledger = optRecord.orElseGet(() -> PriorityLedger.initiate(workId));
                Either<String, PrioritizableEvent<ID, P>> eitherEvent = ledger.sequence(proposedPriority, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates adjusting the work priority level.
     */
    public IO<Either<String, PriorityLedger<ID, P, C>>> reprioritize(ID workId, P proposedPriority, C comment, Instant now) {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(proposedPriority);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        PrioritizableRequest<ID, P, C> request = requests.get(workId);
        if (request == null) {
            return IO.of(Either.left("Prioritizable request domain object not registered: " + workId));
        }

        return coordinator.coordinate(
            workId,
            "reprioritize",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Priority ledger not found: " + workId);
                }
                PriorityLedger<ID, P, C> ledger = optRecord.get();
                Either<String, PrioritizableEvent<ID, P>> eitherEvent = ledger.reprioritize(proposedPriority, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates deferring the priority execution.
     */
    public IO<Either<String, PriorityLedger<ID, P, C>>> defer(ID workId, Instant deferredUntil, C comment, Instant now) {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(deferredUntil);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        PrioritizableRequest<ID, P, C> request = requests.get(workId);
        if (request == null) {
            return IO.of(Either.left("Prioritizable request domain object not registered: " + workId));
        }

        return coordinator.coordinate(
            workId,
            "defer",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Priority ledger not found: " + workId);
                }
                PriorityLedger<ID, P, C> ledger = optRecord.get();
                Either<String, PrioritizableEvent<ID, P>> eitherEvent = ledger.defer(deferredUntil, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates expediting the priority execution.
     */
    public IO<Either<String, PriorityLedger<ID, P, C>>> expedite(ID workId, C comment, Instant now) {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        PrioritizableRequest<ID, P, C> request = requests.get(workId);
        if (request == null) {
            return IO.of(Either.left("Prioritizable request domain object not registered: " + workId));
        }

        return coordinator.coordinate(
            workId,
            "expedite",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Priority ledger not found: " + workId);
                }
                PriorityLedger<ID, P, C> ledger = optRecord.get();
                Either<String, PrioritizableEvent<ID, P>> eitherEvent = ledger.expedite(comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }
}
