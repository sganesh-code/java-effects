package io.effects.samples.ecommerce.domain;

import io.effects.recipes.reservable.models.Hold;
import io.effects.recipes.reservable.models.Reservation;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class WarehouseTest {

    @Test
    void testReserveAndConfirmStock() {
        // Initialize Warehouse directly with explicit item dependency and capacity
        Warehouse warehouse = new Warehouse("TEST-WH-1", "ITEM-123", 10);

        Instant now = Instant.parse("2026-07-09T10:00:00Z");

        // 1. Holding 4 items should succeed
        Hold<String, Integer> hold = warehouse.reserveStock("ITEM-123", "actor-1", 4, 300, now);
        assertNotNull(hold);
        assertEquals("ITEM-123", hold.resourceId());
        assertEquals(4, hold.quantity());

        // 2. Confirming the hold should succeed
        Reservation<String, Integer> reservation = warehouse.confirmStock(hold.holdId(), now.plusSeconds(50));
        assertNotNull(reservation);
        assertEquals(hold.holdId(), reservation.holdId());
    }

    @Test
    void testOverbookingStockThrowsException() {
        // Initialize Warehouse directly with explicit item dependency and capacity
        Warehouse warehouse = new Warehouse("TEST-WH-2", "ITEM-456", 5);

        Instant now = Instant.parse("2026-07-09T10:00:00Z");

        // Reserving more than capacity (6 units when only 5 are available) should throw an exception
        assertThrows(RuntimeException.class, () -> {
            warehouse.reserveStock("ITEM-456", "actor-2", 6, 300, now);
        });
    }
}
