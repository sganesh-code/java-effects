package io.effects.recipes.reservable;

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
 * An Object-Oriented "Recipe" representing a Reservation Router or Gateway.
 * It manages routing messages, orchestrating holds, expiries, releases, and confirmations.
 * 
 * In accordance with our architectural boundary, this process represents the monadic infrastructure
 * engine, and thus exposes purely monadic APIs (returning IO) to allow lazy, virtual-thread execution,
 * cancellation, and pipeline composition.
 */
public final class ReservationProcess<ID, Q> {
    private final StateRepository<ID, ResourceLedger<ID, Q>> ledgerRepository;
    private final StateRepository<String, Hold<ID, Q>> holdRepository;
    private final EventPublisher<ReservationEvent<ID, Q>> eventPublisher;
    private final TelemetryPort telemetryPort;
    private final ConcurrentMap<ID, ReservableResource<ID, Q>> resources = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public ReservationProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public ReservationProcess(
        StateRepository<ID, ResourceLedger<ID, Q>> ledgerRepository,
        StateRepository<String, Hold<ID, Q>> holdRepository,
        EventPublisher<ReservationEvent<ID, Q>> eventPublisher,
        TelemetryPort telemetryPort
    ) {
        this.ledgerRepository = Objects.requireNonNull(ledgerRepository);
        this.holdRepository = Objects.requireNonNull(holdRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.telemetryPort = Objects.requireNonNull(telemetryPort);
    }

    /**
     * Registers a behavioral resource domain object.
     */
    public IO<Void> add(ID resourceId, ReservableResource<ID, Q> resource) {
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(resource);
        return IO.delay(() -> {
            resources.put(resourceId, resource);
            return null;
        }).flatMap(v -> ledgerRepository.save(resourceId, new ResourceLedger<>()));
    }

    /**
     * Attempts to acquire a lease/hold on a resource.
     */
    public IO<Either<String, Hold<ID, Q>>> hold(ID resourceId, String actorId, Q quantity, long holdDurationSeconds, Instant now) {
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(now);

        ReservableResource<ID, Q> resource = resources.get(resourceId);
        if (resource == null) {
            return IO.of(Either.left("Resource not registered: " + resourceId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> ledgerRepository.find(resourceId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, Hold<ID, Q>>left("Resource ledger not found: " + resourceId));
                }

                ResourceLedger<ID, Q> ledger = optLedger.get();
                String holdId = UUID.randomUUID().toString();
                Instant expiresAt = now.plusSeconds(holdDurationSeconds);

                // Invoke pure domain logic synchronously
                Either<String, Hold<ID, Q>> tryHoldResult = resource.tryHold(ledger, holdId, actorId, quantity, now, expiresAt);
                if (tryHoldResult.isRight()) {
                    Hold<ID, Q> hold = tryHoldResult.getRight();
                    return holdRepository.save(holdId, hold)
                        .flatMap(v -> ledgerRepository.save(resourceId, ledger))
                        .flatMap(v -> eventPublisher.publish(new HoldCreated<>(holdId, resourceId, actorId, quantity, expiresAt, now)))
                        .flatMap(v -> telemetryPort.recordSuccess("reservable", resourceId.toString() + ":hold"))
                        .flatMap(v -> telemetryPort.recordDuration("reservable", resourceId.toString(), System.currentTimeMillis() - startTime))
                        .map(v -> Either.<String, Hold<ID, Q>>right(hold));
                } else {
                    String reason = tryHoldResult.getLeft();
                    return eventPublisher.publish(new HoldRejected<>(resourceId, actorId, quantity, reason, now))
                        .flatMap(v -> telemetryPort.recordFailure("reservable", resourceId.toString() + ":hold", reason))
                        .flatMap(v -> telemetryPort.recordDuration("reservable", resourceId.toString(), System.currentTimeMillis() - startTime))
                        .map(v -> Either.<String, Hold<ID, Q>>left(reason));
                }
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Confirms/Finalizes a hold into a full reservation.
     */
    public IO<Either<String, Reservation<ID, Q>>> confirm(String holdId, Instant now) {
        Objects.requireNonNull(holdId);
        Objects.requireNonNull(now);

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> holdRepository.find(holdId))
            .bind((startTime, optHold) -> {
                if (optHold.isEmpty()) {
                    return IO.of(Either.<String, Reservation<ID, Q>>left("Hold not found: " + holdId));
                }

                Hold<ID, Q> hold = optHold.get();
                ID resourceId = hold.resourceId();
                ReservableResource<ID, Q> resource = resources.get(resourceId);
                if (resource == null) {
                    return IO.of(Either.<String, Reservation<ID, Q>>left("Resource not registered: " + resourceId));
                }

                return ledgerRepository.find(resourceId)
                    .flatMap(optLedger -> {
                        if (optLedger.isEmpty()) {
                            return IO.of(Either.<String, Reservation<ID, Q>>left("Resource ledger not found: " + resourceId));
                        }

                        ResourceLedger<ID, Q> ledger = optLedger.get();
                        String reservationId = UUID.randomUUID().toString();

                        // Invoke pure domain logic synchronously
                        Either<String, Reservation<ID, Q>> eitherRes = resource.tryConfirm(ledger, hold, reservationId, now);
                        if (eitherRes.isRight()) {
                            Reservation<ID, Q> reservation = eitherRes.getRight();
                            Hold<ID, Q> updatedHold = hold.withStatus(Hold.Status.CONFIRMED);

                            return holdRepository.save(holdId, updatedHold)
                                .flatMap(v -> ledgerRepository.save(resourceId, ledger))
                                .flatMap(v -> eventPublisher.publish(new HoldConfirmed<>(holdId, reservationId, resourceId, hold.actorId(), hold.quantity(), now)))
                                .flatMap(v -> telemetryPort.recordSuccess("reservable", resourceId.toString() + ":confirm"))
                                .flatMap(v -> telemetryPort.recordDuration("reservable", resourceId.toString(), System.currentTimeMillis() - startTime))
                                .map(v -> Either.<String, Reservation<ID, Q>>right(reservation));
                        } else {
                            String reason = eitherRes.getLeft();
                            return telemetryPort.recordFailure("reservable", resourceId.toString() + ":confirm", reason)
                                .flatMap(v -> telemetryPort.recordDuration("reservable", resourceId.toString(), System.currentTimeMillis() - startTime))
                                .map(v -> Either.<String, Reservation<ID, Q>>left(reason));
                        }
                    });
            })
            .yield((startTime, optHold, result) -> result);
    }

    /**
     * Explicitly releases/voids a hold.
     */
    public IO<Void> release(String holdId) {
        Objects.requireNonNull(holdId);

        return ForIO.set(holdRepository.find(holdId))
            .bind(optHold -> {
                if (optHold.isEmpty()) {
                    return IO.of(null); // Idempotent success
                }

                Hold<ID, Q> hold = optHold.get();
                ID resourceId = hold.resourceId();
                ReservableResource<ID, Q> resource = resources.get(resourceId);

                return ledgerRepository.find(resourceId)
                    .flatMap(optLedger -> {
                        if (optLedger.isPresent() && resource != null) {
                            ResourceLedger<ID, Q> ledger = optLedger.get();
                            ledger.recordRelease(holdId);
                            resource.onRelease(hold);
                            return ledgerRepository.save(resourceId, ledger);
                        }
                        return IO.of(null);
                    })
                    .flatMap(v -> holdRepository.remove(holdId))
                    .flatMap(v -> eventPublisher.publish(new HoldReleased<>(holdId, resourceId, Instant.now())));
            })
            .yield((optHold, result) -> result);
    }

    /**
     * Expire/voids a hold after its lease duration.
     */
    public IO<Void> expire(String holdId) {
        Objects.requireNonNull(holdId);

        return ForIO.set(holdRepository.find(holdId))
            .bind(optHold -> {
                if (optHold.isEmpty()) {
                    return IO.of(null); // Idempotent success
                }

                Hold<ID, Q> hold = optHold.get();
                ID resourceId = hold.resourceId();
                ReservableResource<ID, Q> resource = resources.get(resourceId);

                return ledgerRepository.find(resourceId)
                    .flatMap(optLedger -> {
                        if (optLedger.isPresent() && resource != null) {
                            ResourceLedger<ID, Q> ledger = optLedger.get();
                            ledger.recordExpire(holdId);
                            resource.onExpire(hold);
                            return ledgerRepository.save(resourceId, ledger);
                        }
                        return IO.of(null);
                    })
                    .flatMap(v -> holdRepository.remove(holdId))
                    .flatMap(v -> eventPublisher.publish(new HoldExpired<>(holdId, resourceId, Instant.now())));
            })
            .yield((optHold, result) -> result);
    }
}