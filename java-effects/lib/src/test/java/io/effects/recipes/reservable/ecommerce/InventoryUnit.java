package io.effects.recipes.reservable.ecommerce;

import io.effects.Either;
import io.effects.recipes.reservable.Hold;
import io.effects.recipes.reservable.Reservation;
import io.effects.recipes.reservable.ReservableResource;
import io.effects.recipes.reservable.ResourceLedger;
import java.time.Instant;
import java.util.Optional;

/**
 * E-commerce domain representation of bulk inventory stock level for a SKU.
 * It is completely stateless and synchronous! It contains NO monadic references (no IO)
 * or concurrency boilerplate, delegating bookkeeping to the ledger provided upon invocation.
 */
public final class InventoryUnit implements ReservableResource {
    private final String sku;
    private final String productName;

    public InventoryUnit(String sku, String productName) {
        this.sku = sku;
        this.productName = productName;
    }

    @Override
    public Either<String, Hold> tryHold(ResourceLedger ledger, String holdId, String actorId, int quantity, Instant now, Instant expiresAt) {
        Hold hold = new Hold(holdId, actorId, sku, quantity, expiresAt, Hold.Status.HELD);
        
        // Pure Domain Policy via Double Dispatch: active capacity + requested quantity must not exceed total stock capacity
        return ledger.recordHold(hold, now, (active, total) -> active + quantity <= total 
            ? Optional.empty() 
            : Optional.of("Insufficient stock level for SKU: " + sku)
        );
    }

    @Override
    public Either<String, Reservation> tryConfirm(ResourceLedger ledger, Hold hold, String reservationId, Instant now) {
        Reservation reservation = new Reservation(reservationId, hold.holdId(), hold.actorId(), sku, hold.quantity(), now);
        
        // Pure Domain Policy via Double Dispatch: the hold must still be in active HELD status
        return ledger.recordConfirmation(hold, reservation, (total, h) -> h.status() == Hold.Status.HELD 
            ? Optional.empty() 
            : Optional.of("Invalid hold status")
        );
    }

    @Override
    public void onRelease(Hold hold) {
        // Inventory released -> Perform e-commerce specific side-effects directly
    }

    @Override
    public void onExpire(Hold hold) {
        // Cart item reservation expired -> Perform e-commerce specific side-effects directly
    }
}
