package io.effects.samples.ecommerce.domain;

import io.effects.Either;
import io.effects.ports.EventPublisher;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.reservable.*;
import java.time.Instant;

/**
 * First-class business object representing a physical warehouse inventory system.
 * It encapsulates and directly implements the Reservable recipe process to participate
 * natively in the inventory ledger's state transitions.
 */
public class Warehouse implements ReservableResource<String, Integer> {
    private final String warehouseId;
    private final ReservationProcess<String, Integer> inventoryProcess;
    private final String itemId;
    private final int maxCapacity;

    public Warehouse(String warehouseId, String itemId, int capacity, EventPublisher<ReservationEvent<String, Integer>> publisher) {
        this.warehouseId = warehouseId;
        this.itemId = itemId;
        this.maxCapacity = capacity;
        this.inventoryProcess = new ReservationProcess<String, Integer>(
            new InMemoryStateRepository<>(), 
            new InMemoryStateRepository<>(), 
            publisher, 
            new NoOpTelemetryPort()
        );
        
        // Eagerly register ourselves as the behavior provider
        this.inventoryProcess.register(itemId, this).unsafeRunSync();
    }

    public Warehouse(String warehouseId, String itemId, int capacity) {
        this(warehouseId, itemId, capacity, new InMemoryEventPublisher<>());
    }

    // --- High-Level Business Behaviors ---

    public Hold<String, Integer> reserveStock(String itemId, String actorId, int quantity, int ttlSeconds, Instant time) {
        DomainLogger.info("[RESERVATION] Placing a hold on " + quantity + " " + itemId + " in warehouse: " + warehouseId);
        var res = inventoryProcess.hold(itemId, actorId, quantity, ttlSeconds, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Stock hold failed: " + res.getLeft());
        }
        Hold<String, Integer> hold = res.getRight();
        DomainLogger.info("[RESERVATION] Hold acquired. Hold ID: " + hold.holdId() + ". Expires at: " + hold.expiresAt());
        return hold;
    }

    public Reservation<String, Integer> confirmStock(String holdId, Instant time) {
        var res = inventoryProcess.confirm(holdId, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Stock confirmation failed: " + res.getLeft());
        }
        Reservation<String, Integer> reservation = res.getRight();
        DomainLogger.info("[RESERVATION] Reservation confirmed successfully! Reservation ID: " + reservation.reservationId());
        return reservation;
    }

    // --- ReservableResource Interface Implementation ---

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
