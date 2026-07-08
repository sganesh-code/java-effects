package io.effects.recipes.reservable;

import io.effects.Either;
import io.effects.IO;
import io.effects.ForIO;
import io.effects.recipes.ports.reservable.*;
import io.effects.recipes.adapters.reservable.InMemoryStateRepository;
import io.effects.recipes.adapters.reservable.InMemoryEventPublisher;
import io.effects.recipes.adapters.reservable.NoOpTelemetryPort;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An Object-Oriented "Recipe" representing a Reservation Router or Gateway.
 * It manages routing messages, orchestrating holds, expiries, releases, and confirmations.
 * 
 * In accordance with our architectural boundary, this process represents the monadic infrastructure
 * engine, and thus exposes purely monadic APIs (returning IO) to allow lazy, virtual-thread execution,
 * cancellation, and pipeline composition.
 */
public final class ReservationProcess {
    private final StateRepository stateRepository;
    private final EventPublisher eventPublisher;
    private final TelemetryPort telemetryPort;
    private final ConcurrentMap<String, ReservableResource> resources = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public ReservationProcess() {
        this(new InMemoryStateRepository(), new InMemoryEventPublisher(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public ReservationProcess(
        StateRepository stateRepository,
        EventPublisher eventPublisher,
        TelemetryPort telemetryPort
    ) {
        this.stateRepository = Objects.requireNonNull(stateRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.telemetryPort = Objects.requireNonNull(telemetryPort);
    }

    /**
     * Registers a scarce behavioral resource and configures its capacity.
     */
    public IO<Void> add(String resourceId, ReservableResource resource, int capacity) {
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(resource);
        return IO.delay(() -> {
            resources.put(resourceId, resource);
            return null;
        }).flatMap(v -> stateRepository.saveLedger(resourceId, new ResourceLedger(capacity)));
    }

    /**
     * Delegates holding capacity to the targeted resource.
     * Safely wraps the resource's pure synchronous evaluation inside a lazy, monadic IO block.
     */
    public IO<Either<String, Hold>> hold(String resourceId, String actorId, int quantity, int expiryDurationSecs, Instant now) {
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(now);

        ReservableResource resource = resources.get(resourceId);
        if (resource == null) {
            return IO.of(Either.left("Resource not found"));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> stateRepository.findLedger(resourceId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, Hold>left("Resource ledger not found"));
                }
                ResourceLedger ledger = optLedger.get();
                String holdId = UUID.randomUUID().toString();
                Instant expiresAt = now.plusSeconds(expiryDurationSecs);

                // Invoke pure synchronous domain trial
                Either<String, Hold> eitherHold = resource.tryHold(ledger, holdId, actorId, quantity, now, expiresAt);
                if (eitherHold.isRight()) {
                    Hold hold = eitherHold.getRight();
                    
                    return stateRepository.saveHold(hold, resourceId)
                        .flatMap(v -> stateRepository.saveLedger(resourceId, ledger))
                        .flatMap(v -> eventPublisher.publish(new HoldCreated(holdId, resourceId, actorId, quantity, expiresAt, now)))
                        .flatMap(v -> telemetryPort.recordHoldSuccess(resourceId))
                        .flatMap(v -> telemetryPort.recordHoldDuration(resourceId, System.currentTimeMillis() - startTime))
                        .map(v -> Either.<String, Hold>right(hold));
                } else {
                    String reason = eitherHold.getLeft();
                    return eventPublisher.publish(new HoldRejected(resourceId, actorId, quantity, reason, now))
                        .flatMap(v -> telemetryPort.recordHoldFailure(resourceId, reason))
                        .flatMap(v -> telemetryPort.recordHoldDuration(resourceId, System.currentTimeMillis() - startTime))
                        .map(v -> Either.<String, Hold>left(reason));
                }
            })
            .yield((startTime, optLedger, res) -> res);
    }

    /**
     * Delegates confirming an active hold to the corresponding resource.
     * Safely wraps the resource's pure synchronous evaluation inside a lazy, monadic IO block.
     */
    public IO<Either<String, Reservation>> confirm(String holdId, Instant now) {
        Objects.requireNonNull(holdId);
        Objects.requireNonNull(now);

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> stateRepository.findResourceIdForHold(holdId))
            .bind((startTime, optResourceId) -> {
                if (optResourceId.isEmpty()) {
                    return IO.of(Optional.empty());
                }
                return stateRepository.findHold(holdId);
            })
            .bind((startTime, optResourceId, optHold) -> {
                if (optResourceId.isEmpty() || optHold.isEmpty()) {
                    return IO.of(Optional.empty());
                }
                String resourceId = optResourceId.get();
                return stateRepository.findLedger(resourceId);
            })
            .bind((startTime, optResourceId, optHold, optLedger) -> {
                if (optResourceId.isEmpty()) {
                    return IO.of(Either.<String, Reservation>left("Hold not found"));
                }
                String resourceId = optResourceId.get();
                ReservableResource resource = resources.get(resourceId);
                if (resource == null) {
                    return IO.of(Either.<String, Reservation>left("Resource not found"));
                }
                if (optHold.isEmpty()) {
                    return IO.of(Either.<String, Reservation>left("Hold not found"));
                }
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, Reservation>left("Resource ledger not found"));
                }

                Hold hold = optHold.get();
                ResourceLedger ledger = optLedger.get();
                String reservationId = UUID.randomUUID().toString();

                // Invoke pure synchronous domain confirmation
                Either<String, Reservation> eitherRes = resource.tryConfirm(ledger, hold, reservationId, now);
                if (eitherRes.isRight()) {
                    Reservation reservation = eitherRes.getRight();
                    Hold updatedHold = hold.withStatus(Hold.Status.CONFIRMED);

                    return stateRepository.saveHold(updatedHold, resourceId)
                        .flatMap(v -> stateRepository.saveLedger(resourceId, ledger))
                        .flatMap(v -> eventPublisher.publish(new HoldConfirmed(holdId, reservationId, resourceId, hold.actorId(), hold.quantity(), now)))
                        .flatMap(v -> telemetryPort.recordConfirmationSuccess(resourceId))
                        .flatMap(v -> telemetryPort.recordHoldDuration(resourceId, System.currentTimeMillis() - startTime))
                        .map(v -> Either.<String, Reservation>right(reservation));
                } else {
                    String reason = eitherRes.getLeft();
                    return telemetryPort.recordConfirmationFailure(resourceId, reason)
                        .flatMap(v -> telemetryPort.recordHoldDuration(resourceId, System.currentTimeMillis() - startTime))
                        .map(v -> Either.<String, Reservation>left(reason));
                }
            })
            .yield((startTime, optResourceId, optHold, optLedger, res) -> res);
    }

    /**
     * Orchestrates releasing a hold: updates the ledger state first, then notifies the resource.
     * Safely wraps the evaluations inside a lazy, monadic IO block.
     */
    public IO<Void> release(String holdId) {
        Objects.requireNonNull(holdId);
        return ForIO.set(stateRepository.findResourceIdForHold(holdId))
            .bind(optResourceId -> {
                if (optResourceId.isEmpty()) {
                    return IO.of(Optional.<Hold>empty());
                }
                return stateRepository.findHold(holdId);
            })
            .bind((optResourceId, optHold) -> {
                if (optResourceId.isEmpty() || optHold.isEmpty()) {
                    return IO.of(Optional.<ResourceLedger>empty());
                }
                return stateRepository.findLedger(optResourceId.get());
            })
            .bind((optResourceId, optHold, optLedger) -> {
                if (optResourceId.isEmpty() || optHold.isEmpty() || optLedger.isEmpty()) {
                    return IO.of(null);
                }
                String resourceId = optResourceId.get();
                ReservableResource resource = resources.get(resourceId);
                if (resource == null) {
                    return IO.of(null);
                }
                Hold hold = optHold.get();
                ResourceLedger ledger = optLedger.get();
                
                // Execute pure synchronous operations
                ledger.recordRelease(holdId);
                resource.onRelease(hold);

                return stateRepository.removeHold(holdId)
                    .flatMap(v -> stateRepository.saveLedger(resourceId, ledger))
                    .flatMap(v -> eventPublisher.publish(new HoldReleased(holdId, resourceId, Instant.now())));
            })
            .yield((optResourceId, optHold, optLedger, result) -> result);
    }

    /**
     * Orchestrates expiring a hold: updates the ledger state first, then notifies the resource.
     * Safely wraps the evaluations inside a lazy, monadic IO block.
     */
    public IO<Void> expire(String holdId) {
        Objects.requireNonNull(holdId);
        return ForIO.set(stateRepository.findResourceIdForHold(holdId))
            .bind(optResourceId -> {
                if (optResourceId.isEmpty()) {
                    return IO.of(Optional.empty());
                }
                return stateRepository.findHold(holdId);
            })
            .bind((optResourceId, optHold) -> {
                if (optResourceId.isEmpty() || optHold.isEmpty()) {
                    return IO.of(Optional.empty());
                }
                return stateRepository.findLedger(optResourceId.get());
            })
            .bind((optResourceId, optHold, optLedger) -> {
                if (optResourceId.isEmpty() || optHold.isEmpty() || optLedger.isEmpty()) {
                    return IO.of(null);
                }
                String resourceId = optResourceId.get();
                ReservableResource resource = resources.get(resourceId);
                if (resource == null) {
                    return IO.of(null);
                }
                Hold hold = optHold.get();
                ResourceLedger ledger = optLedger.get();
                
                // Execute pure synchronous operations
                ledger.recordExpire(holdId);
                resource.onExpire(hold);

                return stateRepository.removeHold(holdId)
                    .flatMap(v -> stateRepository.saveLedger(resourceId, ledger))
                    .flatMap(v -> eventPublisher.publish(new HoldExpired(holdId, resourceId, Instant.now())));
            })
            .yield((optResourceId, optHold, optLedger, result) -> result);
    }
}
