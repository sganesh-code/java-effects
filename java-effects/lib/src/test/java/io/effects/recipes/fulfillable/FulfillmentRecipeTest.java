package io.effects.recipes.fulfillable;

import io.effects.Either;
import io.effects.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.StateRepository;
import io.effects.ports.TelemetryPort;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FulfillmentRecipeTest {

    // A concrete, behavioral domain request representing a warehouse order.
    // Exposes NO getter APIs for identity or initiators, satisfying our pure OOP guidelines.
    private static final class WarehouseOrder implements FulfillableRequest {

        WarehouseOrder() {}

        @Override
        public Either<String, Void> evaluateAllocation(FulfillmentLedger ledger, int quantity, Instant now) {
            if (quantity <= 0) {
                return Either.left("Allocation quantity must be positive");
            }
            int remainingToAllocate = ledger.totalQuantity() - ledger.allocatedQuantity();
            // Invariant: cannot allocate more than remaining total order amount
            if (quantity > remainingToAllocate) {
                return Either.left("Allocation quantity " + quantity + " exceeds remaining order quantity: " + remainingToAllocate);
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluatePackaging(FulfillmentLedger ledger, int quantity, Instant now) {
            if (quantity <= 0) {
                return Either.left("Packaging quantity must be positive");
            }
            int remainingToPackage = ledger.allocatedQuantity() - ledger.packagedQuantity();
            // Sequential Progression Invariant: cannot package items that are not first allocated
            if (quantity > remainingToPackage) {
                return Either.left("Packaging quantity " + quantity + " exceeds remaining unpacked allocated quantity: " + remainingToPackage);
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateDispatch(FulfillmentLedger ledger, Instant now) {
            // Sequential Progression Invariant: must package everything allocated before shipping
            if (ledger.packagedQuantity() < ledger.allocatedQuantity() || ledger.packagedQuantity() == 0) {
                return Either.left("Cannot dispatch: order is not fully packaged (packaged " + ledger.packagedQuantity() + "/" + ledger.allocatedQuantity() + ")");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateCompletion(FulfillmentLedger ledger, Instant now) {
            // Must be dispatched before completing
            if (ledger.status() != FulfillmentLedger.Status.DISPATCHED) {
                return Either.left("Cannot complete: order has not been dispatched yet");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateRelease(FulfillmentLedger ledger, int quantity, Instant now) {
            if (quantity <= 0) {
                return Either.left("Release quantity must be positive");
            }
            // Release is only allowed during active allocation/packaging phases
            if (ledger.status() == FulfillmentLedger.Status.DISPATCHED || ledger.status() == FulfillmentLedger.Status.COMPLETED) {
                return Either.left("Cannot release items: order has already been dispatched or completed");
            }
            // Cannot release more than currently allocated or packaged
            int maxDeductible = Math.max(ledger.allocatedQuantity(), ledger.packagedQuantity());
            if (quantity > maxDeductible) {
                return Either.left("Release quantity " + quantity + " exceeds active outstanding items: " + maxDeductible);
            }
            return Either.right(null);
        }
    }

    // 1. Sequential Progression Invariant & Capacity Limits
    @Test
    void testFulfillmentSequentialProgression() {
        FulfillmentProcess process = new FulfillmentProcess();
        WarehouseOrder order = new WarehouseOrder();
        process.register("ful-1", order).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T16:00:00Z");

        // Initiate ledger for 5 items
        process.initiate("ful-1", 5).unsafeRunSync();

        // Fails because we cannot package before allocating
        Either<String, FulfillmentLedger> badPackage = process.packageItems("ful-1", "user-1", 3, "Box up keyboard", t0).unsafeRunSync();
        assertTrue(badPackage.isLeft());
        assertTrue(badPackage.getLeft().contains("Cannot package in current status"));

        // Allocate 3 (Partial allocation) -> Succeeds
        Either<String, FulfillmentLedger> allocResult = process.allocate("ful-1", "user-1", 3, "Reserve stock", t0).unsafeRunSync();
        assertTrue(allocResult.isRight());
        FulfillmentLedger ledger = allocResult.getRight();

        assertEquals(FulfillmentLedger.Status.ALLOCATING, ledger.status());
        assertEquals(3, ledger.allocatedQuantity());
        assertEquals(0, ledger.packagedQuantity());

        // Allocate 3 more -> Fails (total limit is 5, remaining is 2)
        Either<String, FulfillmentLedger> overAlloc = process.allocate("ful-1", "user-1", 3, "Reserve extra", t0).unsafeRunSync();
        assertTrue(overAlloc.isLeft());

        // Allocate remaining 2 -> Succeeds
        Either<String, FulfillmentLedger> completeAlloc = process.allocate("ful-1", "user-1", 2, "Reserve remaining", t0).unsafeRunSync();
        assertTrue(completeAlloc.isRight());
        assertEquals(5, completeAlloc.getRight().allocatedQuantity());
    }

    // 2. Multi-step Fulfillment & Dispatch / Delivery Transitions
    @Test
    void testFulfillmentShipmentLifecycle() {
        InMemoryStateRepository<String, FulfillmentLedger> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<FulfillmentEvent> publisher = new InMemoryEventPublisher<>();
        FulfillmentProcess process = new FulfillmentProcess(repository, publisher, new NoOpTelemetryPort());

        WarehouseOrder order = new WarehouseOrder();
        process.register("ful-2", order).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T16:30:00Z");

        process.initiate("ful-2", 3).unsafeRunSync();

        // 1. Allocate 3
        process.allocate("ful-2", "user-2", 3, "Allocated screen", t0).unsafeRunSync();

        // 2. Dispatch fails because packaging is incomplete
        Either<String, FulfillmentLedger> badDispatch = process.dispatch("ful-2", "user-2", "Ship to customer", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(badDispatch.isLeft());

        // 3. Package 3
        Either<String, FulfillmentLedger> packageResult = process.packageItems("ful-2", "user-2", 3, "Box screen", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(packageResult.isRight());
        assertEquals(FulfillmentLedger.Status.PACKAGING, packageResult.getRight().status());
        assertEquals(3, packageResult.getRight().packagedQuantity());

        // 4. Dispatch -> Succeeds (Transitions to DISPATCHED)
        Either<String, FulfillmentLedger> dispatchResult = process.dispatch("ful-2", "carrier-1", "On delivery van", t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(dispatchResult.isRight());
        assertEquals(FulfillmentLedger.Status.DISPATCHED, dispatchResult.getRight().status());

        // 5. Complete delivery -> Succeeds (Transitions to COMPLETED)
        Either<String, FulfillmentLedger> completeResult = process.complete("ful-2", "carrier-1", "Delivered at porch", t0.plusSeconds(40)).unsafeRunSync();
        assertTrue(completeResult.isRight());
        FulfillmentLedger finalLedger = completeResult.getRight();

        assertEquals(FulfillmentLedger.Status.COMPLETED, finalLedger.status());
        assertTrue(finalLedger.isTerminal());

        // Verify Events
        List<FulfillmentEvent> events = publisher.getPublishedEvents();
        assertEquals(3, events.size()); // alloc, dispatch, complete
        assertTrue(events.get(0) instanceof FulfillmentAllocated);
        assertTrue(events.get(1) instanceof FulfillmentDispatched);
        assertTrue(events.get(2) instanceof FulfillmentCompleted);
    }

    // 3. Item Release Invariant
    @Test
    void testFulfillmentRelease() {
        InMemoryStateRepository<String, FulfillmentLedger> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<FulfillmentEvent> publisher = new InMemoryEventPublisher<>();
        FulfillmentProcess process = new FulfillmentProcess(repository, publisher, new NoOpTelemetryPort());

        WarehouseOrder order = new WarehouseOrder();
        process.register("ful-3", order).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T17:00:00Z");

        process.initiate("ful-3", 4).unsafeRunSync();

        process.allocate("ful-3", "user-3", 4, "Allocate chairs", t0).unsafeRunSync();
        process.packageItems("ful-3", "user-3", 4, "Box chairs", t0.plusSeconds(10)).unsafeRunSync();

        // Release 2 chairs back to stock -> Succeeds
        Either<String, FulfillmentLedger> releaseResult = process.release("ful-3", "user-3", 2, "Customer reduced order", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(releaseResult.isRight());
        FulfillmentLedger ledger = releaseResult.getRight();

        assertEquals(FulfillmentLedger.Status.PACKAGING, ledger.status());
        assertEquals(2, ledger.allocatedQuantity());
        assertEquals(2, ledger.packagedQuantity());

        // Verify release events
        List<FulfillmentEvent> events = publisher.getPublishedEvents();
        assertEquals(2, events.size()); // allocated, released
        assertTrue(events.get(1) instanceof FulfillmentReleased);
        assertEquals(2, ((FulfillmentReleased) events.get(1)).quantity());

        // Dispatch 2 chairs successfully
        process.dispatch("ful-3", "carrier-2", "Ship remaining", t0.plusSeconds(30)).unsafeRunSync();

        // Release fails because order is already DISPATCHED (Transit Terminal Transition!)
        Either<String, FulfillmentLedger> postDispatchRelease = process.release("ful-3", "user-3", 1, "Try release in-transit", t0.plusSeconds(40)).unsafeRunSync();
        assertTrue(postDispatchRelease.isLeft());
    }
}
