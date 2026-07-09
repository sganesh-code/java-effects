package io.effects.recipes.reservable.healthcare;

import io.effects.Either;
import io.effects.recipes.reservable.Hold;
import io.effects.recipes.reservable.Reservation;
import io.effects.recipes.reservable.ReservableResource;
import io.effects.recipes.reservable.ResourceLedger;
import java.time.Instant;
import java.util.Optional;

/**
 * Healthcare domain representation of a clinic calendar appointment slot.
 * It is completely stateless and synchronous! It contains NO monadic references (no IO)
 * or concurrency boilerplate, delegating bookkeeping to the ledger provided upon invocation.
 */
public final class AppointmentSlot implements ReservableResource<String, Integer> {
    private final String slotId;
    private final String doctorName;

    public AppointmentSlot(String slotId, String doctorName) {
        this.slotId = slotId;
        this.doctorName = doctorName;
    }

    @Override
    public Either<String, Hold<String, Integer>> tryHold(
        ResourceLedger<String, Integer> ledger, 
        String holdId, 
        String actorId, 
        Integer quantity, 
        Instant now, 
        Instant expiresAt
    ) {
        if (quantity != 1) {
            return Either.left("Cannot hold quantity other than 1 on a clinic appointment slot");
        }

        Hold<String, Integer> hold = new Hold<>(holdId, actorId, slotId, quantity, expiresAt, Hold.Status.HELD);

        // Pure Domain Policy via Double Dispatch
        return ledger.recordHold(hold, now, (holds, reservations) -> {
            boolean hasActiveHold = holds.stream().anyMatch(h -> now.isBefore(h.expiresAt()));
            boolean hasActiveReservation = !reservations.isEmpty();
            if (hasActiveHold || hasActiveReservation) {
                return Optional.of("Clinic appointment slot is already reserved or held: " + slotId);
            }
            return Optional.empty();
        });
    }

    @Override
    public Either<String, Reservation<String, Integer>> tryConfirm(
        ResourceLedger<String, Integer> ledger, 
        Hold<String, Integer> hold, 
        String reservationId, 
        Instant now
    ) {
        Reservation<String, Integer> reservation = new Reservation<>(reservationId, hold.holdId(), hold.actorId(), slotId, hold.quantity(), now);

        // Pure Domain Policy via Double Dispatch
        return ledger.recordConfirmation(hold, reservation, (reservations, h) -> h.status() == Hold.Status.HELD 
            ? Optional.empty() 
            : Optional.of("Invalid hold status")
        );
    }

    @Override
    public void onRelease(Hold<String, Integer> hold) {
        // Patient released slot -> Perform healthcare-specific side-effects directly
    }

    @Override
    public void onExpire(Hold<String, Integer> hold) {
        // Hold expired -> Perform clinic-specific side-effects directly
    }
}