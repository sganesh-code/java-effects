package io.effects.recipes.reservable.healthcare;

import io.effects.Either;
import io.effects.recipes.reservable.Hold;
import io.effects.recipes.reservable.Reservation;
import io.effects.recipes.reservable.ReservableResource;
import io.effects.recipes.reservable.ResourceLedger;
import java.time.Instant;
import java.util.Optional;

/**
 * Healthcare domain representation of a scarce, single-capacity clinician slot.
 * It is completely stateless and synchronous! It contains NO monadic references (no IO)
 * or concurrency boilerplate, delegating bookkeeping to the ledger provided upon invocation.
 */
public final class AppointmentSlot implements ReservableResource {
    private final String slotId;
    private final String clinicianName;
    private final String specialty;

    public AppointmentSlot(String slotId, String clinicianName, String specialty) {
        this.slotId = slotId;
        this.clinicianName = clinicianName;
        this.specialty = specialty;
    }

    @Override
    public Either<String, Hold> tryHold(ResourceLedger ledger, String holdId, String actorId, int quantity, Instant now, Instant expiresAt) {
        if (quantity != 1) {
            return Either.left("Cannot hold quantity other than 1 on a clinic appointment slot");
        }
        Hold hold = new Hold(holdId, actorId, slotId, 1, expiresAt, Hold.Status.HELD);
        
        // Pure Domain Policy via Double Dispatch: the slot must be empty (active capacity == 0)
        return ledger.recordHold(hold, now, (active, total) -> active == 0 
            ? Optional.empty() 
            : Optional.of("Appointment slot is already held or confirmed")
        );
    }

    @Override
    public Either<String, Reservation> tryConfirm(ResourceLedger ledger, Hold hold, String reservationId, Instant now) {
        Reservation reservation = new Reservation(reservationId, hold.holdId(), hold.actorId(), slotId, 1, now);
        
        // Pure Domain Policy via Double Dispatch: the hold must still be in active HELD status
        return ledger.recordConfirmation(hold, reservation, (total, h) -> h.status() == Hold.Status.HELD 
            ? Optional.empty() 
            : Optional.of("Invalid hold status")
        );
    }

    @Override
    public void onRelease(Hold hold) {
        // Patient released slot -> Perform healthcare-specific side-effects directly
    }

    @Override
    public void onExpire(Hold hold) {
        // Hold expired -> Perform clinic-specific side-effects directly
    }
}
