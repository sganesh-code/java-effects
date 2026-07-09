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
public final class InventoryUnit implements ReservableResource<String, Integer> {
    private final String sku;
    private final String productName;
    private final int totalCapacity;

    public InventoryUnit(String sku, String productName, int totalCapacity) {
        this.sku = sku;
        this.productName = productName;
        this.totalCapacity = totalCapacity;
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
        Hold<String, Integer> hold = new Hold<>(holdId, actorId, sku, quantity, expiresAt, Hold.Status.HELD);
        
        // Pure Domain Policy via Double Dispatch
        return ledger.recordHold(hold, now, (holds, reservations) -> {
            int activeHeld = holds.stream()
                .filter(h -> now.isBefore(h.expiresAt()))
                .mapToInt(Hold::quantity)
                .sum();
            int activeConfirmed = reservations.stream()
                .mapToInt(Reservation::quantity)
                .sum();
            int totalActive = activeHeld + activeConfirmed;
            if (totalActive + quantity > totalCapacity) {
                return Optional.of("Insufficient stock level for SKU: " + sku);
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
        Reservation<String, Integer> reservation = new Reservation<>(reservationId, hold.holdId(), hold.actorId(), sku, hold.quantity(), now);
        
        // Pure Domain Policy via Double Dispatch
        return ledger.recordConfirmation(hold, reservation, (reservations, h) -> h.status() == Hold.Status.HELD 
            ? Optional.empty() 
            : Optional.of("Invalid hold status")
        );
    }

    @Override
    public void onRelease(Hold<String, Integer> hold) {
        // Inventory released -> Perform e-commerce specific side-effects directly
    }

    @Override
    public void onExpire(Hold<String, Integer> hold) {
        // Cart item reservation expired -> Perform e-commerce specific side-effects directly
    }
}