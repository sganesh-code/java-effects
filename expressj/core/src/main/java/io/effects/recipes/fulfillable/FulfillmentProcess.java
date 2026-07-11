package io.effects.recipes.fulfillable;

import io.effects.Either;
import io.effects.IO;
import io.effects.ForIO;
import io.effects.ports.EventPublisher;
import io.effects.ports.StateRepository;
import io.effects.ports.TelemetryPort;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An Object-Oriented "Recipe" representing a Fulfillment Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside FulfillmentLedger).
 */
public final class FulfillmentProcess<ID, Q> {
    private final StateRepository<ID, FulfillmentLedger<ID, Q>> repository;
    private final EventPublisher<FulfillmentEvent<ID, Q>> publisher;
    private final TelemetryPort telemetry;
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
    }

    /**
     * Registers a behavioral fulfillment request domain object.
     */
    public IO<Void> register(ID fulfillmentId, FulfillableRequest<ID, Q> request) {
        Objects.requireNonNull(fulfillmentId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            fulfillments.put(fulfillmentId, request);
            return null;
        });
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

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(fulfillmentId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, FulfillmentLedger<ID, Q>>left("Fulfillment ledger not found: " + fulfillmentId));
                }

                FulfillmentLedger<ID, Q> ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, FulfillmentEvent<ID, Q>> eitherEvent = ledger.allocate(actorId, detail, comment, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger<ID, Q>>left(eitherEvent.getLeft()));
                }

                FulfillmentEvent<ID, Q> event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("fulfillable", fulfillmentId.toString() + ":allocate"))
                    .flatMap(v -> telemetry.recordDuration("fulfillable", fulfillmentId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, FulfillmentLedger<ID, Q>>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
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

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(fulfillmentId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, FulfillmentLedger<ID, Q>>left("Fulfillment ledger not found: " + fulfillmentId));
                }

                FulfillmentLedger<ID, Q> ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, FulfillmentEvent<ID, Q>> eitherEvent = ledger.packageItems(actorId, detail, comment, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger<ID, Q>>left(eitherEvent.getLeft()));
                }

                FulfillmentEvent<ID, Q> event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("fulfillable", fulfillmentId.toString() + ":package"))
                    .flatMap(v -> telemetry.recordDuration("fulfillable", fulfillmentId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, FulfillmentLedger<ID, Q>>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
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

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(fulfillmentId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, FulfillmentLedger<ID, Q>>left("Fulfillment ledger not found: " + fulfillmentId));
                }

                FulfillmentLedger<ID, Q> ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, FulfillmentEvent<ID, Q>> eitherEvent = ledger.dispatch(actorId, comment, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger<ID, Q>>left(eitherEvent.getLeft()));
                }

                FulfillmentEvent<ID, Q> event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("fulfillable", fulfillmentId.toString() + ":dispatch"))
                    .flatMap(v -> telemetry.recordDuration("fulfillable", fulfillmentId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, FulfillmentLedger<ID, Q>>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
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

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(fulfillmentId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, FulfillmentLedger<ID, Q>>left("Fulfillment ledger not found: " + fulfillmentId));
                }

                FulfillmentLedger<ID, Q> ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, FulfillmentEvent<ID, Q>> eitherEvent = ledger.complete(actorId, comment, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger<ID, Q>>left(eitherEvent.getLeft()));
                }

                FulfillmentEvent<ID, Q> event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("fulfillable", fulfillmentId.toString() + ":complete"))
                    .flatMap(v -> telemetry.recordDuration("fulfillable", fulfillmentId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, FulfillmentLedger<ID, Q>>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
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

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(fulfillmentId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, FulfillmentLedger<ID, Q>>left("Fulfillment ledger not found: " + fulfillmentId));
                }

                FulfillmentLedger<ID, Q> ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, FulfillmentEvent<ID, Q>> eitherEvent = ledger.release(actorId, detail, comment, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger<ID, Q>>left(eitherEvent.getLeft()));
                }

                FulfillmentEvent<ID, Q> event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("fulfillable", fulfillmentId.toString() + ":release"))
                    .flatMap(v -> telemetry.recordDuration("fulfillable", fulfillmentId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, FulfillmentLedger<ID, Q>>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Finds and loads the fulfillment ledger state by its identifier.
     */
    public IO<java.util.Optional<FulfillmentLedger<ID, Q>>> find(ID fulfillmentId) {
        Objects.requireNonNull(fulfillmentId);
        return repository.find(fulfillmentId);
    }
}