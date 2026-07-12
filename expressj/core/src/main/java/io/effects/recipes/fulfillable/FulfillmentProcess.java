package io.effects.recipes.fulfillable;

import io.effects.recipes.fulfillable.models.*;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * An Object-Oriented "Recipe" representing a Fulfillment Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside FulfillmentLedger).
 */
public final class FulfillmentProcess<ID, Q> implements Recipe<ID, FulfillableRequest<ID, Q>> {
    private final StateRepository<ID, FulfillmentLedger<ID, Q>> repository;
    private final EventPublisher<FulfillmentEvent<ID, Q>> publisher;
    private final TelemetryPort telemetry;
    private final ProcessCoordinator<ID, FulfillmentLedger<ID, Q>, FulfillmentEvent<ID, Q>> coordinator;
    private final ConcurrentMap<ID, FulfillableRequest<ID, Q>> fulfillments = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public FulfillmentProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public FulfillmentProcess(
        StateRepository<ID, FulfillmentLedger<ID, Q>> repository,
        EventPublisher<FulfillmentEvent<ID, Q>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.coordinator = new ProcessCoordinator<>(repository, publisher, telemetry, "fulfillable");
    }

    /**
     * Registers a behavioral fulfillment request domain object.
     */
    @Override
    public IO<Void> register(ID fulfillmentId, FulfillableRequest<ID, Q> request) {
        Objects.requireNonNull(fulfillmentId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            fulfillments.put(fulfillmentId, request);
            return null;
        });
    }

    @Override
    public IO<Void> unregister(ID fulfillmentId) {
        Objects.requireNonNull(fulfillmentId);
        return IO.delay(() -> {
            fulfillments.remove(fulfillmentId);
            return null;
        });
    }

    @Override
    public IO<Boolean> isRegistered(ID fulfillmentId) {
        Objects.requireNonNull(fulfillmentId);
        return IO.delay(() -> fulfillments.containsKey(fulfillmentId));
    }

    /**
     * Creates/Initiates a new fulfillment ledger.
     */
    public IO<Void> initiate(ID fulfillmentId) {
        Objects.requireNonNull(fulfillmentId);
        return repository.save(fulfillmentId, FulfillmentLedger.initiate(fulfillmentId));
    }

    /**
     * Allocates items to a fulfillment order.
     */
    public IO<Either<String, FulfillmentLedger<ID, Q>>> allocate(ID fulfillmentId, String actorId, Q detail, String comment, Instant now) {
        Objects.requireNonNull(fulfillmentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        FulfillableRequest<ID, Q> request = fulfillments.get(fulfillmentId);
        if (request == null) {
            return IO.of(Either.left("Fulfillment domain object not registered: " + fulfillmentId));
        }

        return coordinator.coordinate(
            fulfillmentId,
            "allocate",
            optLedger -> {
                if (optLedger.isEmpty()) {
                    return Either.left("Fulfillment ledger not found: " + fulfillmentId);
                }
                FulfillmentLedger<ID, Q> ledger = optLedger.get();
                Either<String, FulfillmentEvent<ID, Q>> eitherEvent = ledger.allocate(actorId, detail, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Packages allocated items.
     */
    public IO<Either<String, FulfillmentLedger<ID, Q>>> packageItems(ID fulfillmentId, String actorId, Q detail, String comment, Instant now) {
        Objects.requireNonNull(fulfillmentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        FulfillableRequest<ID, Q> request = fulfillments.get(fulfillmentId);
        if (request == null) {
            return IO.of(Either.left("Fulfillment domain object not registered: " + fulfillmentId));
        }

        return coordinator.coordinate(
            fulfillmentId,
            "package",
            optLedger -> {
                if (optLedger.isEmpty()) {
                    return Either.left("Fulfillment ledger not found: " + fulfillmentId);
                }
                FulfillmentLedger<ID, Q> ledger = optLedger.get();
                Either<String, FulfillmentEvent<ID, Q>> eitherEvent = ledger.packageItems(actorId, detail, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Dispatches packaged items.
     */
    public IO<Either<String, FulfillmentLedger<ID, Q>>> dispatch(ID fulfillmentId, String actorId, String comment, Instant now) {
        Objects.requireNonNull(fulfillmentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        FulfillableRequest<ID, Q> request = fulfillments.get(fulfillmentId);
        if (request == null) {
            return IO.of(Either.left("Fulfillment domain object not registered: " + fulfillmentId));
        }

        return coordinator.coordinate(
            fulfillmentId,
            "dispatch",
            optLedger -> {
                if (optLedger.isEmpty()) {
                    return Either.left("Fulfillment ledger not found: " + fulfillmentId);
                }
                FulfillmentLedger<ID, Q> ledger = optLedger.get();
                Either<String, FulfillmentEvent<ID, Q>> eitherEvent = ledger.dispatch(actorId, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Completes fulfillment/delivery.
     */
    public IO<Either<String, FulfillmentLedger<ID, Q>>> complete(ID fulfillmentId, String actorId, String comment, Instant now) {
        Objects.requireNonNull(fulfillmentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        FulfillableRequest<ID, Q> request = fulfillments.get(fulfillmentId);
        if (request == null) {
            return IO.of(Either.left("Fulfillment domain object not registered: " + fulfillmentId));
        }

        return coordinator.coordinate(
            fulfillmentId,
            "complete",
            optLedger -> {
                if (optLedger.isEmpty()) {
                    return Either.left("Fulfillment ledger not found: " + fulfillmentId);
                }
                FulfillmentLedger<ID, Q> ledger = optLedger.get();
                Either<String, FulfillmentEvent<ID, Q>> eitherEvent = ledger.complete(actorId, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Releases allocated or packaged items back to inventory.
     */
    public IO<Either<String, FulfillmentLedger<ID, Q>>> release(ID fulfillmentId, String actorId, Q detail, String comment, Instant now) {
        Objects.requireNonNull(fulfillmentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        FulfillableRequest<ID, Q> request = fulfillments.get(fulfillmentId);
        if (request == null) {
            return IO.of(Either.left("Fulfillment domain object not registered: " + fulfillmentId));
        }

        return coordinator.coordinate(
            fulfillmentId,
            "release",
            optLedger -> {
                if (optLedger.isEmpty()) {
                    return Either.left("Fulfillment ledger not found: " + fulfillmentId);
                }
                FulfillmentLedger<ID, Q> ledger = optLedger.get();
                Either<String, FulfillmentEvent<ID, Q>> eitherEvent = ledger.release(actorId, detail, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Finds and loads the fulfillment ledger state by its identifier.
     */
    public IO<java.util.Optional<FulfillmentLedger<ID, Q>>> find(ID fulfillmentId) {
        Objects.requireNonNull(fulfillmentId);
        return repository.find(fulfillmentId);
    }
}
