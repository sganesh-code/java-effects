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
import io.effects.recipes.ports.reservable.*;
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
    private final StateRepository<String, ResourceLedger> ledgerRepository;
    private final StateRepository<String, Hold> holdRepository;
    private final EventPublisher<ReservationEvent> eventPublisher;
    private final TelemetryPort telemetryPort;
    private final ConcurrentMap<String, ReservableResource> resources = new ConcurrentHashMap<>();

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
        StateRepository<String, ResourceLedger> ledgerRepository,
        StateRepository<String, Hold> holdRepository,
        EventPublisher<ReservationEvent> eventPublisher,
        TelemetryPort telemetryPort
    ) {
        this.ledgerRepository = Objects.requireNonNull(ledgerRepository);
        this.holdRepository = Objects.requireNonNull(holdRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.telemetryPort = Objects.requireNonNull(telemetryPort);
    }

    /**
     * Registers a behavioral resource domain object and configures its capacity.
     */
    public IO<Void> add(String resourceId, ReservableResource resource, int capacity) {
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(resource);
        return IO.delay(() -> {
            resources.put(resourceId, resource);
            return null;
        }).flatMap(v -> ledgerRepository.save(resourceId, new ResourceLedger(capacity)));
    }

    /**
     * Attempts to acquire a lease/hold on a resource.
     */
    public IO<Either<String, Hold>> hold(String resourceId, String actorId, int quantity, long holdDurationSeconds, Instant now) {
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(now);

        ReservableResource resource = resources.get(resourceId);
        if (resource == null) {
            return IO.of(Either.left("Resource not registered: " + resourceId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> ledgerRepository.find(resourceId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, Hold>left("Resource ledger not found: " + resourceId));
                }

                ResourceLedger ledger = optLedger.get();
                String holdId = UUID.randomUUID().toString();
                Instant expiresAt = now.plusSeconds(holdDurationSeconds);

                // Invoke pure domain logic synchronously
                Either<String, Hold> tryHoldResult = resource.tryHold(ledger, holdId, actorId, quantity, now, expiresAt);
                if (tryHoldResult.isRight()) {
                    Hold hold = tryHoldResult.getRight();
                    return holdRepository.save(holdId, hold)
                        .flatMap(v -> ledgerRepository.save(resourceId, ledger))
                        .flatMap(v -> eventPublisher.publish(new HoldCreated(holdId, resourceId, actorId, quantity, expiresAt, now)))
                        .flatMap(v -> telemetryPort.recordSuccess("reservable", resourceId + ":hold"))
                        .flatMap(v -> telemetryPort.recordDuration("reservable", resourceId, System.currentTimeMillis() - startTime))
                        .map(v -> Either.<String, Hold>right(hold));
                } else {
                    String reason = tryHoldResult.getLeft();
                    return eventPublisher.publish(new HoldRejected(resourceId, actorId, quantity, reason, now))
                        .flatMap(v -> telemetryPort.recordFailure("reservable", resourceId + ":hold", reason))
                        .flatMap(v -> telemetryPort.recordDuration("reservable", resourceId, System.currentTimeMillis() - startTime))
                        .map(v -> Either.<String, Hold>left(reason));
                }
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Confirms/Finalizes a hold into a full reservation.
     */
    public IO<Either<String, Reservation>> confirm(String holdId, Instant now) {
        Objects.requireNonNull(holdId);
        Objects.requireNonNull(now);

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> holdRepository.find(holdId))
            .bind((startTime, optHold) -> {
                if (optHold.isEmpty()) {
                    return IO.of(Either.<String, Reservation>left("Hold not found: " + holdId));
                }

                Hold hold = optHold.get();
                String resourceId = hold.resourceId();
                ReservableResource resource = resources.get(resourceId);
                if (resource == null) {
                    return IO.of(Either.<String, Reservation>left("Resource not registered: " + resourceId));
                }

                return ledgerRepository.find(resourceId)
                    .flatMap(optLedger -> {
                        if (optLedger.isEmpty()) {
                            return IO.of(Either.<String, Reservation>left("Resource ledger not found: " + resourceId));
                        }

                        ResourceLedger ledger = optLedger.get();
                        String reservationId = UUID.randomUUID().toString();

                        // Invoke pure domain logic synchronously
                        Either<String, Reservation> eitherRes = resource.tryConfirm(ledger, hold, reservationId, now);
                        if (eitherRes.isRight()) {
                            Reservation reservation = eitherRes.getRight();
                            Hold updatedHold = hold.withStatus(Hold.Status.CONFIRMED);

                            return holdRepository.save(holdId, updatedHold)
                                .flatMap(v -> ledgerRepository.save(resourceId, ledger))
                                .flatMap(v -> eventPublisher.publish(new HoldConfirmed(holdId, reservationId, resourceId, hold.actorId(), hold.quantity(), now)))
                                .flatMap(v -> telemetryPort.recordSuccess("reservable", resourceId + ":confirm"))
                                .flatMap(v -> telemetryPort.recordDuration("reservable", resourceId, System.currentTimeMillis() - startTime))
                                .map(v -> Either.<String, Reservation>right(reservation));
                        } else {
                            String reason = eitherRes.getLeft();
                            return telemetryPort.recordFailure("reservable", resourceId + ":confirm", reason)
                                .flatMap(v -> telemetryPort.recordDuration("reservable", resourceId, System.currentTimeMillis() - startTime))
                                .map(v -> Either.<String, Reservation>left(reason));
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

                Hold hold = optHold.get();
                String resourceId = hold.resourceId();
                ReservableResource resource = resources.get(resourceId);

                return ledgerRepository.find(resourceId)
                    .flatMap(optLedger -> {
                        if (optLedger.isPresent() && resource != null) {
                            ResourceLedger ledger = optLedger.get();
                            ledger.recordRelease(holdId);
                            resource.onRelease(hold);
                            return ledgerRepository.save(resourceId, ledger);
                        }
                        return IO.of(null);
                    })
                    .flatMap(v -> holdRepository.remove(holdId))
                    .flatMap(v -> eventPublisher.publish(new HoldReleased(holdId, resourceId, Instant.now())));
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

                Hold hold = optHold.get();
                String resourceId = hold.resourceId();
                ReservableResource resource = resources.get(resourceId);

                return ledgerRepository.find(resourceId)
                    .flatMap(optLedger -> {
                        if (optLedger.isPresent() && resource != null) {
                            ResourceLedger ledger = optLedger.get();
                            ledger.recordExpire(holdId);
                            resource.onExpire(hold);
                            return ledgerRepository.save(resourceId, ledger);
                        }
                        return IO.of(null);
                    })
                    .flatMap(v -> holdRepository.remove(holdId))
                    .flatMap(v -> eventPublisher.publish(new HoldExpired(holdId, resourceId, Instant.now())));
            })
            .yield((optHold, result) -> result);
    }
}
