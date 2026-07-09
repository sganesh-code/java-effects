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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An Object-Oriented "Recipe" representing a Fulfillment Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside FulfillmentLedger).
 */
public final class FulfillmentProcess {
    private final StateRepository<String, FulfillmentLedger> repository;
    private final EventPublisher<FulfillmentEvent> publisher;
    private final TelemetryPort telemetry;
    private final ConcurrentMap<String, FulfillableRequest> fulfillments = new ConcurrentHashMap<>();

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
        StateRepository<String, FulfillmentLedger> repository,
        EventPublisher<FulfillmentEvent> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    /**
     * Registers a behavioral fulfillment request domain object.
     */
    public IO<Void> register(String fulfillmentId, FulfillableRequest request) {
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
    public IO<Void> initiate(String fulfillmentId, int totalQuantity) {
        Objects.requireNonNull(fulfillmentId);
        return repository.save(fulfillmentId, FulfillmentLedger.initiate(fulfillmentId, totalQuantity));
    }

    /**
     * Allocates items to a fulfillment order.
     */
    public IO<Either<String, FulfillmentLedger>> allocate(String fulfillmentId, String actorId, int quantity, String comment, Instant now) {
        Objects.requireNonNull(fulfillmentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        FulfillableRequest request = fulfillments.get(fulfillmentId);
        if (request == null) {
            return IO.of(Either.left("Fulfillment domain object not registered: " + fulfillmentId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(fulfillmentId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, FulfillmentLedger>left("Fulfillment ledger not found: " + fulfillmentId));
                }

                FulfillmentLedger ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, FulfillmentEvent> eitherEvent = ledger.allocate(actorId, quantity, comment, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger>left(eitherEvent.getLeft()));
                }

                FulfillmentEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("fulfillable", fulfillmentId + ":allocate"))
                    .flatMap(v -> telemetry.recordDuration("fulfillable", fulfillmentId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, FulfillmentLedger>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Packages allocated items.
     */
    public IO<Either<String, FulfillmentLedger>> packageItems(String fulfillmentId, String actorId, int quantity, String comment, Instant now) {
        Objects.requireNonNull(fulfillmentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        FulfillableRequest request = fulfillments.get(fulfillmentId);
        if (request == null) {
            return IO.of(Either.left("Fulfillment domain object not registered: " + fulfillmentId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(fulfillmentId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, FulfillmentLedger>left("Fulfillment ledger not found: " + fulfillmentId));
                }

                FulfillmentLedger ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, FulfillmentEvent> eitherEvent = ledger.packageItems(actorId, quantity, comment, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger>left(eitherEvent.getLeft()));
                }

                FulfillmentEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("fulfillable", fulfillmentId + ":package"))
                    .flatMap(v -> telemetry.recordDuration("fulfillable", fulfillmentId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, FulfillmentLedger>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Dispatches packaged items.
     */
    public IO<Either<String, FulfillmentLedger>> dispatch(String fulfillmentId, String actorId, String comment, Instant now) {
        Objects.requireNonNull(fulfillmentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        FulfillableRequest request = fulfillments.get(fulfillmentId);
        if (request == null) {
            return IO.of(Either.left("Fulfillment domain object not registered: " + fulfillmentId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(fulfillmentId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, FulfillmentLedger>left("Fulfillment ledger not found: " + fulfillmentId));
                }

                FulfillmentLedger ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, FulfillmentEvent> eitherEvent = ledger.dispatch(actorId, comment, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger>left(eitherEvent.getLeft()));
                }

                FulfillmentEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("fulfillable", fulfillmentId + ":dispatch"))
                    .flatMap(v -> telemetry.recordDuration("fulfillable", fulfillmentId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, FulfillmentLedger>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Completes fulfillment/delivery.
     */
    public IO<Either<String, FulfillmentLedger>> complete(String fulfillmentId, String actorId, String comment, Instant now) {
        Objects.requireNonNull(fulfillmentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        FulfillableRequest request = fulfillments.get(fulfillmentId);
        if (request == null) {
            return IO.of(Either.left("Fulfillment domain object not registered: " + fulfillmentId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(fulfillmentId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, FulfillmentLedger>left("Fulfillment ledger not found: " + fulfillmentId));
                }

                FulfillmentLedger ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, FulfillmentEvent> eitherEvent = ledger.complete(actorId, comment, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger>left(eitherEvent.getLeft()));
                }

                FulfillmentEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("fulfillable", fulfillmentId + ":complete"))
                    .flatMap(v -> telemetry.recordDuration("fulfillable", fulfillmentId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, FulfillmentLedger>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Releases allocated or packaged items back to inventory.
     */
    public IO<Either<String, FulfillmentLedger>> release(String fulfillmentId, String actorId, int quantity, String comment, Instant now) {
        Objects.requireNonNull(fulfillmentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        FulfillableRequest request = fulfillments.get(fulfillmentId);
        if (request == null) {
            return IO.of(Either.left("Fulfillment domain object not registered: " + fulfillmentId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(fulfillmentId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, FulfillmentLedger>left("Fulfillment ledger not found: " + fulfillmentId));
                }

                FulfillmentLedger ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, FulfillmentEvent> eitherEvent = ledger.release(actorId, quantity, comment, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger>left(eitherEvent.getLeft()));
                }

                FulfillmentEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("fulfillable", fulfillmentId + ":release"))
                    .flatMap(v -> telemetry.recordDuration("fulfillable", fulfillmentId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, FulfillmentLedger>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }
}
