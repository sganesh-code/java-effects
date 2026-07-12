package io.effects.recipes.reconciliable;

import io.effects.recipes.reconciliable.models.*;
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
 * An Object-Oriented "Recipe" representing a Reconciliation Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside ReconciliationLedger).
 */
public final class ReconciliableProcess<ID, K, E, C> implements Recipe<ID, ReconciliableRequest<ID, K, E, C>> {
    private final StateRepository<ID, ReconciliationLedger<ID, K, E, C>> repository;
    private final EventPublisher<ReconciliationEvent<ID, K>> publisher;
    private final TelemetryPort telemetry;
    private final ProcessCoordinator<ID, ReconciliationLedger<ID, K, E, C>, ReconciliationEvent<ID, K>> coordinator;
    private final ConcurrentMap<ID, ReconciliableRequest<ID, K, E, C>> requests = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public ReconciliableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public ReconciliableProcess(
        StateRepository<ID, ReconciliationLedger<ID, K, E, C>> repository,
        EventPublisher<ReconciliationEvent<ID, K>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.coordinator = new ProcessCoordinator<>(repository, publisher, telemetry, "reconciliable");
    }

    @Override
    public IO<Void> register(ID reconciliationId, ReconciliableRequest<ID, K, E, C> request) {
        Objects.requireNonNull(reconciliationId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            requests.put(reconciliationId, request);
            return null;
        });
    }

    @Override
    public IO<Void> unregister(ID reconciliationId) {
        Objects.requireNonNull(reconciliationId);
        return IO.delay(() -> {
            requests.remove(reconciliationId);
            return null;
        });
    }

    @Override
    public IO<Boolean> isRegistered(ID reconciliationId) {
        Objects.requireNonNull(reconciliationId);
        return IO.delay(() -> requests.containsKey(reconciliationId));
    }

    /**
     * Coordinates the matching of an external item against our internal record.
     */
    public IO<Either<String, ReconciliationLedger<ID, K, E, C>>> match(
        ID reconciliationId, 
        K itemId, 
        E externalItem, 
        C comment, 
        Instant now
    ) {
        Objects.requireNonNull(reconciliationId);
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(externalItem);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        ReconciliableRequest<ID, K, E, C> request = requests.get(reconciliationId);
        if (request == null) {
            return IO.of(Either.left("Reconciliable request domain object not registered: " + reconciliationId));
        }

        return coordinator.coordinate(
            reconciliationId,
            "match",
            optRecord -> {
                ReconciliationLedger<ID, K, E, C> ledger = optRecord.orElseGet(() -> ReconciliationLedger.initiate(reconciliationId));
                Either<String, ReconciliationEvent<ID, K>> eitherEvent = ledger.match(itemId, externalItem, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates the flagging of a discrepancy for a specific item.
     */
    public IO<Either<String, ReconciliationLedger<ID, K, E, C>>> flagDiscrepancy(
        ID reconciliationId, 
        K itemId, 
        String discrepancyCode, 
        C comment, 
        Instant now
    ) {
        Objects.requireNonNull(reconciliationId);
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(discrepancyCode);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        ReconciliableRequest<ID, K, E, C> request = requests.get(reconciliationId);
        if (request == null) {
            return IO.of(Either.left("Reconciliable request domain object not registered: " + reconciliationId));
        }

        return coordinator.coordinate(
            reconciliationId,
            "flagDiscrepancy",
            optRecord -> {
                ReconciliationLedger<ID, K, E, C> ledger = optRecord.orElseGet(() -> ReconciliationLedger.initiate(reconciliationId));
                Either<String, ReconciliationEvent<ID, K>> eitherEvent = ledger.flagDiscrepancy(itemId, discrepancyCode, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates the resolution of a previously flagged discrepancy.
     */
    public IO<Either<String, ReconciliationLedger<ID, K, E, C>>> resolve(
        ID reconciliationId, 
        K itemId, 
        String resolutionType, 
        C comment, 
        Instant now
    ) {
        Objects.requireNonNull(reconciliationId);
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(resolutionType);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        ReconciliableRequest<ID, K, E, C> request = requests.get(reconciliationId);
        if (request == null) {
            return IO.of(Either.left("Reconciliable request domain object not registered: " + reconciliationId));
        }

        return coordinator.coordinate(
            reconciliationId,
            "resolve",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Reconciliation ledger not found for session: " + reconciliationId);
                }
                ReconciliationLedger<ID, K, E, C> ledger = optRecord.get();
                Either<String, ReconciliationEvent<ID, K>> eitherEvent = ledger.resolve(itemId, resolutionType, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }
}
