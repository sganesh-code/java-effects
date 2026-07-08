package io.effects.recipes.reservable;

import io.effects.Either;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;

/**
 * A reusable, thread-safe state container provided by the recipe.
 * It manages all the concurrent bookkeeping (holds, expiries, reservations, capacity math).
 * 
 * In this design, the ledger is completely synchronous and pure, removing monadic IO wrappers
 * from the core bookkeeping process.
 */
public final class ResourceLedger {
    private final int totalCapacity;
    private final ConcurrentMap<String, Hold> activeHolds = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Reservation> activeReservations = new ConcurrentHashMap<>();

    public ResourceLedger(int totalCapacity) {
        if (totalCapacity < 0) {
            throw new IllegalArgumentException("totalCapacity cannot be negative");
        }
        this.totalCapacity = totalCapacity;
    }

    private void cleanup(Instant now) {
        activeHolds.values().removeIf(h -> now.isAfter(h.expiresAt()));
    }

    private int getActiveCapacity(Instant now) {
        cleanup(now);
        int held = activeHolds.values().stream()
            .filter(h -> now.isBefore(h.expiresAt()))
            .mapToInt(Hold::quantity)
            .sum();

        int confirmed = activeReservations.values().stream()
            .mapToInt(Reservation::quantity)
            .sum();

        return held + confirmed;
    }

    /**
     * Double Dispatch: Attempt to record a hold synchronously.
     */
    public Either<String, Hold> recordHold(
        Hold hold, 
        Instant now, 
        BiFunction<Integer, Integer, Optional<String>> businessPolicy
    ) {
        cleanup(now);
        int active = getActiveCapacity(now);
        Optional<String> rejectionReason = businessPolicy.apply(active, totalCapacity);
        if (rejectionReason.isEmpty()) {
            activeHolds.put(hold.holdId(), hold);
            return Either.right(hold);
        }
        return Either.left(rejectionReason.get());
    }

    /**
     * Double Dispatch: Attempt to confirm a hold synchronously.
     */
    public Either<String, Reservation> recordConfirmation(
        Hold hold, 
        Reservation reservation, 
        BiFunction<Integer, Hold, Optional<String>> confirmationPolicy
    ) {
        Hold currentHold = activeHolds.get(hold.holdId());
        if (currentHold == null) {
            // Idempotency: check if already confirmed
            Reservation existing = activeReservations.values().stream()
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

        Optional<String> rejectionReason = confirmationPolicy.apply(totalCapacity, currentHold);
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
    public void recordRelease(String holdId) {
        activeHolds.remove(holdId);
    }

    /**
     * Expires an active hold.
     */
    public void recordExpire(String holdId) {
        activeHolds.remove(holdId);
    }
}
