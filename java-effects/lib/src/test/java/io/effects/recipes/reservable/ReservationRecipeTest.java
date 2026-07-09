package io.effects.recipes.reservable;

import io.effects.Either;
import io.effects.ports.EventPublisher;
import io.effects.ports.StateRepository;
import io.effects.ports.TelemetryPort;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.recipes.reservable.healthcare.AppointmentSlot;
import io.effects.recipes.reservable.ecommerce.InventoryUnit;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReservationRecipeTest {

    // 1. Healthcare Domain validation (capacity = 1, single slot)
    @Test
    void testHealthcareAppointmentScheduling() {
        // Create the routing gateway
        ReservationProcess<String, Integer> clinicProcess = new ReservationProcess<>();
        AppointmentSlot slot = new AppointmentSlot("slot-123", "Dr. Jane Doe");
        
        // Register behavioral resource
        clinicProcess.add("slot-123", slot).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T10:00:00Z");

        // Patient A holds slot (succeeds)
        Either<String, Hold<String, Integer>> holdResultA = clinicProcess.hold("slot-123", "patient-A", 1, 300, t0).unsafeRunSync();
        assertTrue(holdResultA.isRight());
        Hold<String, Integer> holdA = holdResultA.getRight();

        // Patient B requests same slot -> fails internally (slot has hold and capacity is 1, handled synchronously inside domain)
        Either<String, Hold<String, Integer>> holdResultB = clinicProcess.hold("slot-123", "patient-B", 1, 300, t0).unsafeRunSync();
        assertTrue(holdResultB.isLeft());
        assertTrue(holdResultB.getLeft().contains("Clinic appointment slot is already reserved or held"));

        // Patient A confirms appointment before expiry -> succeeds
        Either<String, Reservation<String, Integer>> confirmResult = clinicProcess.confirm(holdA.holdId(), t0.plusSeconds(120)).unsafeRunSync();
        assertTrue(confirmResult.isRight());
        Reservation<String, Integer> reservation = confirmResult.getRight();
        assertNotNull(reservation.reservationId());
    }

    // 2. E-commerce Inventory/Stock validation (capacity = 5, multiple holds)
    @Test
    void testEcommerceInventoryStockLeasing() {
        ReservationProcess<String, Integer> storeProcess = new ReservationProcess<>();
        InventoryUnit skuUnit = new InventoryUnit("sku-widget", "Mechanical Keyboard", 5);
        storeProcess.add("sku-widget", skuUnit).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T12:00:00Z");

        // Shopper A holds 3 units (succeeds)
        Either<String, Hold<String, Integer>> holdResultA = storeProcess.hold("sku-widget", "shopper-A", 3, 300, t0).unsafeRunSync();
        assertTrue(holdResultA.isRight());
        Hold<String, Integer> holdA = holdResultA.getRight();

        // Shopper B requests 3 units -> fails internally (only 2 units left, rejected by the unit's local state)
        Either<String, Hold<String, Integer>> holdResultB1 = storeProcess.hold("sku-widget", "shopper-B", 3, 300, t0).unsafeRunSync();
        assertTrue(holdResultB1.isLeft());
        assertTrue(holdResultB1.getLeft().contains("Insufficient stock level"));

        // Shopper B requests 2 units (succeeds, matching remaining capacity)
        Either<String, Hold<String, Integer>> holdResultB2 = storeProcess.hold("sku-widget", "shopper-B", 2, 300, t0).unsafeRunSync();
        assertTrue(holdResultB2.isRight());
        Hold<String, Integer> holdB = holdResultB2.getRight();

        // Verification of temporal law: Shopper A confirms after expiry -> fails (expire state handled inside resource)
        Instant tExpired = t0.plusSeconds(301); // hold expired at t0 + 300
        Either<String, Reservation<String, Integer>> confirmResultExpired = storeProcess.confirm(holdA.holdId(), tExpired).unsafeRunSync();
        assertTrue(confirmResultExpired.isLeft());
        assertEquals("Hold has expired", confirmResultExpired.getLeft());

        // Verification of release: Shopper B releases hold monadically
        storeProcess.release(holdB.holdId()).unsafeRunSync();

        // Shopper C requests 3 units -> succeeds now because B's 2 units are released and A's 3 units expired
        Either<String, Hold<String, Integer>> holdResultC = storeProcess.hold("sku-widget", "shopper-C", 3, 300, tExpired).unsafeRunSync();
        assertTrue(holdResultC.isRight());
        Hold<String, Integer> holdC = holdResultC.getRight();

        // Verification of Idempotency: confirm hold C twice monadically
        Either<String, Reservation<String, Integer>> confirmResultC1 = storeProcess.confirm(holdC.holdId(), tExpired.plusSeconds(10)).unsafeRunSync();
        assertTrue(confirmResultC1.isRight());
        Reservation<String, Integer> resC1 = confirmResultC1.getRight();

        Either<String, Reservation<String, Integer>> confirmResultC2 = storeProcess.confirm(holdC.holdId(), tExpired.plusSeconds(15)).unsafeRunSync();
        assertTrue(confirmResultC2.isRight());
        Reservation<String, Integer> resC2 = confirmResultC2.getRight();

        // Both confirmations return the identical Reservation object (idempotent success)
        assertEquals(resC1.reservationId(), resC2.reservationId());
    }

    // 3. Test dependency injection of StateRepository, EventPublisher, and TelemetryPort at runtime
    @Test
    void testPortsAndAdaptersRuntimeInjection() {
        InMemoryStateRepository<String, ResourceLedger<String, Integer>> ledgerRepo = new InMemoryStateRepository<>();
        InMemoryStateRepository<String, Hold<String, Integer>> holdRepo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<ReservationEvent<String, Integer>> eventPub = new InMemoryEventPublisher<>();
        
        // Custom tracking telemetry using a simple spy implementation
        class TelemetrySpy implements TelemetryPort {
            int successCalls = 0;
            int failureCalls = 0;
            int durationCalls = 0;

            @Override
            public io.effects.IO<Void> recordSuccess(String context, String operationId) {
                return io.effects.IO.delay(() -> {
                    successCalls++;
                    return null;
                });
            }

            @Override
            public io.effects.IO<Void> recordFailure(String context, String operationId, String reason) {
                return io.effects.IO.delay(() -> {
                    failureCalls++;
                    return null;
                });
            }

            @Override
            public io.effects.IO<Void> recordDuration(String context, String operationId, long durationMs) {
                return io.effects.IO.delay(() -> {
                    durationCalls++;
                    return null;
                });
            }
        }

        TelemetrySpy telemetrySpy = new TelemetrySpy();

        // Inject our custom ports/adapters
        ReservationProcess<String, Integer> customProcess = new ReservationProcess<>(ledgerRepo, holdRepo, eventPub, telemetrySpy);
        AppointmentSlot slot = new AppointmentSlot("slot-ports", "Dr. Jane Doe");
        
        customProcess.add("slot-ports", slot).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T10:00:00Z");

        // 1. Assert hold operation persists and publishes events/telemetry
        Either<String, Hold<String, Integer>> holdResult = customProcess.hold("slot-ports", "patient-ports", 1, 60, t0).unsafeRunSync();
        assertTrue(holdResult.isRight());
        Hold<String, Integer> hold = holdResult.getRight();

        // Check StateRepository persistence
        Optional<Hold<String, Integer>> persistedHold = holdRepo.find(hold.holdId()).unsafeRunSync();
        assertTrue(persistedHold.isPresent());
        assertEquals("patient-ports", persistedHold.get().actorId());

        // Check EventPublisher events
        List<ReservationEvent<String, Integer>> publishedEvents = eventPub.getPublishedEvents();
        assertEquals(1, publishedEvents.size());
        assertTrue(publishedEvents.get(0) instanceof HoldCreated);
        HoldCreated<String, Integer> holdCreatedEvent = (HoldCreated<String, Integer>) publishedEvents.get(0);
        assertEquals(hold.holdId(), holdCreatedEvent.holdId());
        assertEquals("slot-ports", holdCreatedEvent.resourceId());

        // Check TelemetryPort tracking
        assertEquals(1, telemetrySpy.successCalls);
        assertEquals(1, telemetrySpy.durationCalls);
        assertEquals(0, telemetrySpy.failureCalls);

        // 2. Assert confirmation operation persists and publishes events/telemetry
        Either<String, Reservation<String, Integer>> confirmResult = customProcess.confirm(hold.holdId(), t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(confirmResult.isRight());
        Reservation<String, Integer> reservation = confirmResult.getRight();

        // Check state change (Hold is now CONFIRMED)
        Optional<Hold<String, Integer>> updatedHold = holdRepo.find(hold.holdId()).unsafeRunSync();
        assertTrue(updatedHold.isPresent());
        assertEquals(Hold.Status.CONFIRMED, updatedHold.get().status());

        // Check EventPublisher events
        publishedEvents = eventPub.getPublishedEvents();
        assertEquals(2, publishedEvents.size());
        assertTrue(publishedEvents.get(1) instanceof HoldConfirmed);
        HoldConfirmed<String, Integer> holdConfirmedEvent = (HoldConfirmed<String, Integer>) publishedEvents.get(1);
        assertEquals(hold.holdId(), holdConfirmedEvent.holdId());
        assertEquals(reservation.reservationId(), holdConfirmedEvent.reservationId());

        // Check TelemetryPort tracking
        assertEquals(2, telemetrySpy.successCalls);
        assertEquals(2, telemetrySpy.durationCalls); // confirmation also records duration
        assertEquals(0, telemetrySpy.failureCalls);
    }
}