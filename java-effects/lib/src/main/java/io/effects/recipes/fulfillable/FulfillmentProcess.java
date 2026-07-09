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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An Object-Oriented "Recipe" representing a Fulfillment Process Manager.
 * It coordinates routing messages, evaluating domain invariants, persistence, event emission, and telemetry.
 * 
 * In accordance with our architectural boundary, this process represents the monadic infrastructure
 * engine, and thus exposes purely monadic APIs (returning IO) to allow lazy, virtual-thread execution,
 * cancellation, and pipeline composition.
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
        return ledgerRepository().save(fulfillmentId, new FulfillmentLedger(fulfillmentId, totalQuantity));
    }

    private StateRepository<String, FulfillmentLedger> ledgerRepository() {
        return repository;
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
                if (ledger.status() != FulfillmentLedger.Status.INITIAL && ledger.status() != FulfillmentLedger.Status.ALLOCATING) {
                    return IO.of(Either.<String, FulfillmentLedger>left("Cannot allocate in current status: " + ledger.status()));
                }

                // Invoke pure domain double dispatch validation synchronously
                Either<String, Void> eitherValid = request.evaluateAllocation(ledger, quantity, now);
                if (eitherValid.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger>left(eitherValid.getLeft()));
                }

                FulfillmentStep step = new FulfillmentStep(
                    UUID.randomUUID().toString(),
                    actorId,
                    FulfillmentStep.Type.ALLOCATE,
                    quantity,
                    comment,
                    now
                );
                ledger.recordStep(step, FulfillmentLedger.Status.ALLOCATING, quantity, 0);

                FulfillmentEvent event = new FulfillmentAllocated(fulfillmentId, quantity, now);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publisher.publish(event))
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
                if (ledger.status() != FulfillmentLedger.Status.ALLOCATING && ledger.status() != FulfillmentLedger.Status.PACKAGING) {
                    return IO.of(Either.<String, FulfillmentLedger>left("Cannot package in current status: " + ledger.status()));
                }

                // Invoke pure domain double dispatch validation synchronously
                Either<String, Void> eitherValid = request.evaluatePackaging(ledger, quantity, now);
                if (eitherValid.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger>left(eitherValid.getLeft()));
                }

                FulfillmentStep step = new FulfillmentStep(
                    UUID.randomUUID().toString(),
                    actorId,
                    FulfillmentStep.Type.PACKAGE,
                    quantity,
                    comment,
                    now
                );
                ledger.recordStep(step, FulfillmentLedger.Status.PACKAGING, 0, quantity);

                return repository.save(fulfillmentId, ledger)
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
                if (ledger.status() != FulfillmentLedger.Status.PACKAGING) {
                    return IO.of(Either.<String, FulfillmentLedger>left("Cannot dispatch in current status: " + ledger.status()));
                }

                // Invoke pure domain double dispatch validation synchronously
                Either<String, Void> eitherValid = request.evaluateDispatch(ledger, now);
                if (eitherValid.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger>left(eitherValid.getLeft()));
                }

                FulfillmentStep step = new FulfillmentStep(
                    UUID.randomUUID().toString(),
                    actorId,
                    FulfillmentStep.Type.DISPATCH,
                    ledger.packagedQuantity(),
                    comment,
                    now
                );
                ledger.recordStep(step, FulfillmentLedger.Status.DISPATCHED, 0, 0);

                FulfillmentEvent event = new FulfillmentDispatched(fulfillmentId, now);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publisher.publish(event))
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
                if (ledger.status() != FulfillmentLedger.Status.DISPATCHED) {
                    return IO.of(Either.<String, FulfillmentLedger>left("Cannot complete delivery in current status: " + ledger.status()));
                }

                // Invoke pure domain double dispatch validation synchronously
                Either<String, Void> eitherValid = request.evaluateCompletion(ledger, now);
                if (eitherValid.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger>left(eitherValid.getLeft()));
                }

                FulfillmentStep step = new FulfillmentStep(
                    UUID.randomUUID().toString(),
                    actorId,
                    FulfillmentStep.Type.COMPLETE,
                    ledger.packagedQuantity(),
                    comment,
                    now
                );
                ledger.recordStep(step, FulfillmentLedger.Status.COMPLETED, 0, 0);

                FulfillmentEvent event = new FulfillmentCompleted(fulfillmentId, now);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publisher.publish(event))
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
                if (ledger.status() != FulfillmentLedger.Status.ALLOCATING && ledger.status() != FulfillmentLedger.Status.PACKAGING) {
                    return IO.of(Either.<String, FulfillmentLedger>left("Cannot release in current status: " + ledger.status()));
                }

                // Invoke pure domain double dispatch validation synchronously
                Either<String, Void> eitherValid = request.evaluateRelease(ledger, quantity, now);
                if (eitherValid.isLeft()) {
                    return IO.of(Either.<String, FulfillmentLedger>left(eitherValid.getLeft()));
                }

                FulfillmentStep step = new FulfillmentStep(
                    UUID.randomUUID().toString(),
                    actorId,
                    FulfillmentStep.Type.RELEASE,
                    quantity,
                    comment,
                    now
                );

                // Deduct both allocated and packaged quantities as appropriate
                int allocDeduct = Math.min(quantity, ledger.allocatedQuantity());
                int packDeduct = Math.min(quantity, ledger.packagedQuantity());

                FulfillmentLedger.Status nextStatus = (ledger.allocatedQuantity() - allocDeduct) == 0 
                    ? FulfillmentLedger.Status.INITIAL 
                    : ledger.status();

                ledger.recordStep(step, nextStatus, -allocDeduct, -packDeduct);

                FulfillmentEvent event = new FulfillmentReleased(fulfillmentId, quantity, now);

                return repository.save(fulfillmentId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("fulfillable", fulfillmentId + ":release"))
                    .flatMap(v -> telemetry.recordDuration("fulfillable", fulfillmentId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, FulfillmentLedger>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }
}
