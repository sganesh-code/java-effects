package io.effects.recipes.reservable;

import io.effects.Either;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.function.BiFunction;

/**
 * A reusable, thread-safe state container provided by the recipe.
 * It manages all the concurrent bookkeeping (holds, expiries, reservations).
 * 
 * In this design, the ledger is completely synchronous and pure, removing monadic IO wrappers
 * from the core bookkeeping process.
 */
public final class ResourceLedger<ID, Q> {
    private final ConcurrentMap<String, Hold<ID, Q>> activeHolds = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Reservation<ID, Q>> activeReservations = new ConcurrentHashMap<>();

    public ResourceLedger() {}

    private void cleanup(Instant now) {
        activeHolds.values().removeIf(h -> now.isAfter(h.expiresAt()));
    }

    public synchronized List<Hold<ID, Q>> activeHolds(Instant now) {
        cleanup(now);
        return List.copyOf(activeHolds.values());
    }

    public synchronized List<Reservation<ID, Q>> activeReservations() {
        return List.copyOf(activeReservations.values());
    }

    /**
     * Double Dispatch: Attempt to record a hold synchronously.
     */
    public synchronized Either<String, Hold<ID, Q>> recordHold(
        Hold<ID, Q> hold, 
        Instant now, 
        BiFunction<List<Hold<ID, Q>>, List<Reservation<ID, Q>>, Optional<String>> businessPolicy
    ) {
        cleanup(now);
        Optional<String> rejectionReason = businessPolicy.apply(
            List.copyOf(activeHolds.values()), 
            List.copyOf(activeReservations.values())
        );
        if (rejectionReason.isEmpty()) {
            activeHolds.put(hold.holdId(), hold);
            return Either.right(hold);
        }
        return Either.left(rejectionReason.get());
    }

    /**
     * Double Dispatch: Attempt to confirm a hold synchronously.
     */
    public synchronized Either<String, Reservation<ID, Q>> recordConfirmation(
        Hold<ID, Q> hold, 
        Reservation<ID, Q> reservation, 
        BiFunction<List<Reservation<ID, Q>>, Hold<ID, Q>, Optional<String>> confirmationPolicy
    ) {
        Hold<ID, Q> currentHold = activeHolds.get(hold.holdId());
        if (currentHold == null) {
            // Idempotency: check if already confirmed
            Reservation<ID, Q> existing = activeReservations.values().stream()
                .filter(r -> r.holdId().equals(hold.holdId()))
                .findFirst()
                .orElse(null);
            if (existing != null) {
                return Either.right(existing);
            }
            return Either.left("Hold not found or already released/expired");
        }

        if (reservation.confirmedAt().isAfter(currentHold.expiresAt())) {
            activeHolds.remove(hold.holdId());
            return Either.left("Hold has expired");
        }

        Optional<String> rejectionReason = confirmationPolicy.apply(
            List.copyOf(activeReservations.values()),
            currentHold
        );
        if (rejectionReason.isEmpty()) {
            activeHolds.remove(hold.holdId());
            activeReservations.put(reservation.reservationId(), reservation);
            return Either.right(reservation);
        }

        return Either.left(rejectionReason.get());
    }

    /**
     * Releases an active hold.
     */
    public synchronized void recordRelease(String holdId) {
        activeHolds.remove(holdId);
    }

    /**
     * Expires an active hold.
     */
    public synchronized void recordExpire(String holdId) {
        activeHolds.remove(holdId);
    }
}