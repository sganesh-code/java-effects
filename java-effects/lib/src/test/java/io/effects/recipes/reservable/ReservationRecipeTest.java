package io.effects.recipes.reservable;

import io.effects.Either;
import io.effects.recipes.ports.reservable.*;
import io.effects.recipes.adapters.reservable.*;
import io.effects.recipes.reservable.healthcare.AppointmentSlot;
import io.effects.recipes.reservable.ecommerce.InventoryUnit;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ReservationRecipeTest {

    // 1. Healthcare Domain validation (capacity = 1, single slot)
    @Test
    void testHealthcareAppointmentScheduling() {
        // Create the routing gateway
        ReservationProcess clinicProcess = new ReservationProcess();
        AppointmentSlot slot = new AppointmentSlot("slot-123", "Dr. Jane Doe", "Cardiology");
        
        // Register behavioral resource and configure its capacity monadically
        clinicProcess.add("slot-123", slot, 1).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T10:00:00Z");

        // Patient A holds slot (valid)
        Either<String, Hold> holdResultA = clinicProcess.hold("slot-123", "patient-A", 1, 60, t0).unsafeRunSync();
        assertTrue(holdResultA.isRight());
        Hold holdA = holdResultA.getRight();
        assertEquals(Hold.Status.HELD, holdA.status());

        // Patient B attempts to hold same slot -> rejected internally by slot cell's own state machine
        Either<String, Hold> holdResultB = clinicProcess.hold("slot-123", "patient-B", 1, 60, t0).unsafeRunSync();
        assertTrue(holdResultB.isLeft());
        assertTrue(holdResultB.getLeft().contains("already held or confirmed"));

        // Patient A confirms hold into Reservation (scheduled visit)
        Either<String, Reservation> confirmResultA = clinicProcess.confirm(holdA.holdId(), t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(confirmResultA.isRight());
        Reservation reservationA = confirmResultA.getRight();
        assertEquals("patient-A", reservationA.actorId());
        assertEquals("slot-123", reservationA.resourceId());
    }

    // 2. E-commerce Domain validation (capacity = 5, bulk inventory)
    @Test
    void testEcommerceInventoryReservationAndLaws() {
        ReservationProcess storeProcess = new ReservationProcess();
        InventoryUnit skuUnit = new InventoryUnit("sku-widget", "Mechanical Keyboard");
        
        // Register stateless resource and configure stock level monadically
        storeProcess.add("sku-widget", skuUnit, 5).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T12:00:00Z");

        // Shopper A holds 3 units (succeeds)
        Either<String, Hold> holdResultA = storeProcess.hold("sku-widget", "shopper-A", 3, 300, t0).unsafeRunSync();
        assertTrue(holdResultA.isRight());
        Hold holdA = holdResultA.getRight();

        // Shopper B requests 3 units -> fails internally (only 2 units left, rejected by the unit's local state)
        Either<String, Hold> holdResultB1 = storeProcess.hold("sku-widget", "shopper-B", 3, 300, t0).unsafeRunSync();
        assertTrue(holdResultB1.isLeft());
        assertTrue(holdResultB1.getLeft().contains("Insufficient stock level"));

        // Shopper B requests 2 units (succeeds, matching remaining capacity)
        Either<String, Hold> holdResultB2 = storeProcess.hold("sku-widget", "shopper-B", 2, 300, t0).unsafeRunSync();
        assertTrue(holdResultB2.isRight());
        Hold holdB = holdResultB2.getRight();

        // Verification of temporal law: Shopper A confirms after expiry -> fails (expire state handled inside resource)
        Instant tExpired = t0.plusSeconds(301); // hold expired at t0 + 300
        Either<String, Reservation> confirmResultExpired = storeProcess.confirm(holdA.holdId(), tExpired).unsafeRunSync();
        assertTrue(confirmResultExpired.isLeft());
        assertEquals("Hold has expired", confirmResultExpired.getLeft());

        // Verification of release: Shopper B releases hold monadically
        storeProcess.release(holdB.holdId()).unsafeRunSync();

        // Shopper C requests 3 units -> succeeds now because B's 2 units are released and A's 3 units expired
        Either<String, Hold> holdResultC = storeProcess.hold("sku-widget", "shopper-C", 3, 300, tExpired).unsafeRunSync();
        assertTrue(holdResultC.isRight());
        Hold holdC = holdResultC.getRight();

        // Verification of Idempotency: confirm hold C twice monadically
        Either<String, Reservation> confirmResultC1 = storeProcess.confirm(holdC.holdId(), tExpired.plusSeconds(10)).unsafeRunSync();
        assertTrue(confirmResultC1.isRight());
        Reservation resC1 = confirmResultC1.getRight();

        Either<String, Reservation> confirmResultC2 = storeProcess.confirm(holdC.holdId(), tExpired.plusSeconds(15)).unsafeRunSync();
        assertTrue(confirmResultC2.isRight());
        Reservation resC2 = confirmResultC2.getRight();

        // Both confirmations return the identical Reservation object (idempotent success)
        assertEquals(resC1.reservationId(), resC2.reservationId());
    }

    // 3. Test dependency injection of StateRepository, EventPublisher, and TelemetryPort at runtime
    @Test
    void testPortsAndAdaptersRuntimeInjection() {
        InMemoryStateRepository stateRepo = new InMemoryStateRepository();
        InMemoryEventPublisher eventPub = new InMemoryEventPublisher();
        
        // Custom tracking telemetry using a simple spy implementation
        class TelemetrySpy implements TelemetryPort {
            int holdDurationsRecorded = 0;
            int holdSuccesses = 0;
            int holdFailures = 0;
            int confirmationSuccesses = 0;
            int confirmationFailures = 0;

            @Override
            public io.effects.IO<Void> recordHoldDuration(String resourceId, long durationMs) {
                return io.effects.IO.delay(() -> {
                    holdDurationsRecorded++;
                    return null;
                });
            }

            @Override
            public io.effects.IO<Void> recordHoldSuccess(String resourceId) {
                return io.effects.IO.delay(() -> {
                    holdSuccesses++;
                    return null;
                });
            }

            @Override
            public io.effects.IO<Void> recordHoldFailure(String resourceId, String reason) {
                return io.effects.IO.delay(() -> {
                    holdFailures++;
                    return null;
                });
            }

            @Override
            public io.effects.IO<Void> recordConfirmationSuccess(String resourceId) {
                return io.effects.IO.delay(() -> {
                    confirmationSuccesses++;
                    return null;
                });
            }

            @Override
            public io.effects.IO<Void> recordConfirmationFailure(String resourceId, String reason) {
                return io.effects.IO.delay(() -> {
                    confirmationFailures++;
                    return null;
                });
            }
        }

        TelemetrySpy telemetrySpy = new TelemetrySpy();

        // Inject our custom ports/adapters
        ReservationProcess customProcess = new ReservationProcess(stateRepo, eventPub, telemetrySpy);
        AppointmentSlot slot = new AppointmentSlot("slot-ports", "Dr. Jane Doe", "Cardiology");
        
        customProcess.add("slot-ports", slot, 1).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T10:00:00Z");

        // 1. Assert hold operation persists and publishes events/telemetry
        Either<String, Hold> holdResult = customProcess.hold("slot-ports", "patient-ports", 1, 60, t0).unsafeRunSync();
        assertTrue(holdResult.isRight());
        Hold hold = holdResult.getRight();

        // Check StateRepository persistence
        java.util.Optional<Hold> persistedHold = stateRepo.findHold(hold.holdId()).unsafeRunSync();
        assertTrue(persistedHold.isPresent());
        assertEquals("patient-ports", persistedHold.get().actorId());

        // Check EventPublisher events
        java.util.List<ReservationEvent> publishedEvents = eventPub.getPublishedEvents();
        assertEquals(1, publishedEvents.size());
        assertTrue(publishedEvents.get(0) instanceof HoldCreated);
        HoldCreated holdCreatedEvent = (HoldCreated) publishedEvents.get(0);
        assertEquals(hold.holdId(), holdCreatedEvent.holdId());
        assertEquals("slot-ports", holdCreatedEvent.resourceId());

        // Check TelemetryPort tracking
        assertEquals(1, telemetrySpy.holdSuccesses);
        assertEquals(1, telemetrySpy.holdDurationsRecorded);
        assertEquals(0, telemetrySpy.holdFailures);

        // 2. Assert confirmation operation persists and publishes events/telemetry
        Either<String, Reservation> confirmResult = customProcess.confirm(hold.holdId(), t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(confirmResult.isRight());
        Reservation reservation = confirmResult.getRight();

        // Check state change (Hold is now CONFIRMED)
        java.util.Optional<Hold> updatedHold = stateRepo.findHold(hold.holdId()).unsafeRunSync();
        assertTrue(updatedHold.isPresent());
        assertEquals(Hold.Status.CONFIRMED, updatedHold.get().status());

        // Check EventPublisher events
        publishedEvents = eventPub.getPublishedEvents();
        assertEquals(2, publishedEvents.size());
        assertTrue(publishedEvents.get(1) instanceof HoldConfirmed);
        HoldConfirmed holdConfirmedEvent = (HoldConfirmed) publishedEvents.get(1);
        assertEquals(hold.holdId(), holdConfirmedEvent.holdId());
        assertEquals(reservation.reservationId(), holdConfirmedEvent.reservationId());

        // Check TelemetryPort tracking
        assertEquals(1, telemetrySpy.confirmationSuccesses);
        assertEquals(2, telemetrySpy.holdDurationsRecorded); // confirmation also records duration
        assertEquals(0, telemetrySpy.confirmationFailures);
    }
}
