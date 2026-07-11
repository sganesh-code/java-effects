package io.effects.samples.ecommerce.domain;

import io.effects.Either;
import io.effects.ports.EventPublisher;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.reservable.*;
import java.time.Instant;

/**
 * Represents a physical warehouse inventory storage facility. 
 * It is responsible for managing physical item stock, securing temporary stock holds for 
 * pending checkouts, and finalizing inventory allocation once payment is verified.
 */
public class Warehouse implements ReservableResource<String, Integer> {
    private final String warehouseId;
    private final String itemId;
    private final int maxCapacity;
    private final ReservationProcess<String, Integer> inventoryProcess;

    /**
     * Initializes a physical warehouse facility with a designated location identifier, 
     * item storage description, and total maximum physical inventory storage capacity.
     */
    public Warehouse(String warehouseId, String itemId, int capacity, EventPublisher<ReservationEvent<String, Integer>> publisher) {
        this.warehouseId = warehouseId;
        this.itemId = itemId;
        this.maxCapacity = capacity;
        this.inventoryProcess = new ReservationProcess<>(
                new InMemoryStateRepository<>(),
                new InMemoryStateRepository<>(),
                publisher,
                new NoOpTelemetryPort()
        );
        
        this.inventoryProcess.register(itemId, this).unsafeRunSync();
    }

    public Warehouse(String warehouseId, String itemId, int capacity) {
        this(warehouseId, itemId, capacity, new InMemoryEventPublisher<>());
    }

    // --- Core Inventory Operations ---

    /**
     * Secures a temporary stock reservation for a specific purchase order. 
     * Ensures that subsequent checkouts do not overbook the warehouse's physical storage capacity.
     */
    public Hold<String, Integer> reserveStock(String itemId, String orderId, int quantity, int ttlSeconds, Instant time) {
        DomainLogger.info("[INVENTORY] Placing temporary stock hold on " + quantity + " units of " + itemId + " in warehouse: " + warehouseId);
        var res = inventoryProcess.hold(itemId, orderId, quantity, ttlSeconds, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Inventory hold failed: " + res.getLeft());
        }
        Hold<String, Integer> hold = res.getRight();
        DomainLogger.info("[INVENTORY] Stock hold acquired. Hold reference ID: " + hold.holdId() + ". Expires at: " + hold.expiresAt());
        return hold;
    }

    /**
     * Finalizes a temporary stock hold into a permanent inventory allocation. 
     * This moves the reserved stock into a finalized shipping queue.
     */
    public Reservation<String, Integer> confirmStock(String holdId, Instant time) {
        var res = inventoryProcess.confirm(holdId, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Stock finalization failed: " + res.getLeft());
        }
        Reservation<String, Integer> reservation = res.getRight();
        DomainLogger.info("[INVENTORY] Reserved stock confirmed successfully! Allocation ID: " + reservation.reservationId());
        return reservation;
    }

    // --- Internal Business Invariants & Policies ---

    /**
     * Evaluation Policy: Assesses if the warehouse has sufficient stock capacity to support a new hold request.
     */
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
            return Either.left("Insufficient warehouse inventory levels for item [" + itemId + "]. Available: " + (maxCapacity - currentCommitted));
        }

        return Either.right(new Hold<>(holdId, actorId, itemId, quantity, expiresAt, Hold.Status.HELD));
    }

    /**
     * Evaluation Policy: Ensures that a temporary hold is still valid and has not expired before final confirmation is authorized.
     */
    @Override
    public Either<String, Reservation<String, Integer>> tryConfirm(ResourceLedger<String, Integer> ledger, Hold<String, Integer> hold, String reservationId, Instant now) {
        if (now.isAfter(hold.expiresAt())) {
            return Either.left("Inventory stock hold has expired and cannot be confirmed.");
        }
        return Either.right(new Reservation<>(reservationId, hold.holdId(), hold.actorId(), hold.resourceId(), hold.quantity(), now));
    }

    @Override
    public void onRelease(Hold<String, Integer> hold) {
        // Automatically release the reserved quantity back to general stock availability on cancel.
    }

    @Override
    public void onExpire(Hold<String, Integer> hold) {
        // Automatically release the reserved quantity back to general stock availability if the hold expires.
    }
}
