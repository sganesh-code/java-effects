package io.effects.samples.ecommerce.reservable;

import io.effects.Either;
import io.effects.recipes.reservable.*;
import java.time.Instant;

public class WarehouseInventoryPool implements ReservableResource<String, Integer> {
    private final String itemId;
    private final int maxCapacity;

    public WarehouseInventoryPool(String itemId, int maxCapacity) {
        this.itemId = itemId;
        this.maxCapacity = maxCapacity;
    }

    @Override
    public Either<String, Hold<String, Integer>> tryHold(ResourceLedger<String, Integer> ledger, String holdId, String actorId, Integer quantity, Instant now, Instant expiresAt) {
        int currentCommitted = 0;
        for (Hold<String, Integer> activeHold : ledger.activeHolds(now)) {
            currentCommitted += activeHold.quantity();
        }
        for (Reservation<String, Integer> activeRes : ledger.activeReservations()) {
            currentCommitted += activeRes.quantity();
        }

        if (currentCommitted + quantity > maxCapacity) {
            return Either.left("Insufficient warehouse stock level for item " + itemId + ". Available: " + (maxCapacity - currentCommitted));
        }

        return Either.right(new Hold<>(holdId, actorId, itemId, quantity, expiresAt, Hold.Status.HELD));
    }

    @Override
    public Either<String, Reservation<String, Integer>> tryConfirm(ResourceLedger<String, Integer> ledger, Hold<String, Integer> hold, String reservationId, Instant now) {
        if (now.isAfter(hold.expiresAt())) {
            return Either.left("Hold has expired");
        }
        return Either.right(new Reservation<>(reservationId, hold.holdId(), hold.actorId(), hold.resourceId(), hold.quantity(), now));
    }

    @Override
    public void onRelease(Hold<String, Integer> hold) {
        // No-op for this simple simulation
    }

    @Override
    public void onExpire(Hold<String, Integer> hold) {
        // No-op for this simple simulation
    }
}
