package io.effects.recipes.reservable;

import io.effects.recipes.reservable.models.*;

import io.effects.core.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing a scarce resource.
 * 
 * In this design, the consumer's implementation is completely synchronous and pure!
 * It contains NO monadic references (no IO) or threading knowledge.
 * The monadic shell (ReservationProcess) is responsible for lifting these pure synchronous
 * evaluations safely into the lazy, concurrent IO context.
 */
public interface ReservableResource<ID, Q> {

    /**
     * Behavioral Message: Attempt to hold capacity on this resource.
     * Evaluates invariants and records a hold synchronously.
     */
    Either<String, Hold<ID, Q>> tryHold(ResourceLedger<ID, Q> ledger, String holdId, String actorId, Q quantity, Instant now, Instant expiresAt);

    /**
     * Behavioral Message: Attempt to confirm an existing hold into a reservation.
     */
    Either<String, Reservation<ID, Q>> tryConfirm(ResourceLedger<ID, Q> ledger, Hold<ID, Q> hold, String reservationId, Instant now);

    /**
     * Notification Callback: Invoked when a hold is released back to the capacity pool.
     */
    void onRelease(Hold<ID, Q> hold);

    /**
     * Notification Callback: Invoked when a hold is expired.
     */
    void onExpire(Hold<ID, Q> hold);
}