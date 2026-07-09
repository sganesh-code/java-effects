package io.effects.recipes.fulfillable;

import io.effects.Either;
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

    // A custom, clean, non-anemic domain representation of item quantity.
    private record ItemQuantity(int quantity) {
        public ItemQuantity add(ItemQuantity other) {
            return new ItemQuantity(this.quantity + other.quantity);
        }
        public ItemQuantity subtract(ItemQuantity other) {
            return new ItemQuantity(this.quantity - other.quantity);
        }
        public boolean isGreaterThan(ItemQuantity other) {
            return this.quantity > other.quantity;
        }
    }

    // A concrete, behavioral domain request representing a warehouse order.
    // Exposes NO getter APIs for identity or initiators, satisfying our pure OOP guidelines.
    private static final class WarehouseOrder implements FulfillableRequest<String, ItemQuantity> {
        private final int totalQuantity;

        WarehouseOrder(int totalQuantity) {
            this.totalQuantity = totalQuantity;
        }

        private int[] calculateOutstandingAllocatedAndPackaged(FulfillmentLedger<String, ItemQuantity> ledger) {
            int allocated = 0;
            int packaged = 0;
            for (FulfillmentStep<ItemQuantity> step : ledger.history()) {
                switch (step.type()) {
                    case ALLOCATE -> allocated += step.detail().quantity();
                    case PACKAGE -> packaged += step.detail().quantity();
                    case RELEASE -> {
                        int releaseQty = step.detail().quantity();
                        allocated -= Math.min(releaseQty, allocated);
                        packaged -= Math.min(releaseQty, packaged);
                    }
                }
            }
            return new int[] { allocated, packaged };
        }

        @Override
        public Either<String, Void> evaluateAllocation(FulfillmentLedger<String, ItemQuantity> ledger, ItemQuantity detail, Instant now) {
            if (detail.quantity() <= 0) {
                return Either.left("Allocation quantity must be positive");
            }
            int[] outstanding = calculateOutstandingAllocatedAndPackaged(ledger);
            int allocated = outstanding[0];
            int remainingToAllocate = totalQuantity - allocated;
            if (detail.quantity() > remainingToAllocate) {
                return Either.left("Allocation quantity " + detail.quantity() + " exceeds remaining order quantity: " + remainingToAllocate);
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluatePackaging(FulfillmentLedger<String, ItemQuantity> ledger, ItemQuantity detail, Instant now) {
            if (detail.quantity() <= 0) {
                return Either.left("Packaging quantity must be positive");
            }
            int[] outstanding = calculateOutstandingAllocatedAndPackaged(ledger);
            int allocated = outstanding[0];
            int packaged = outstanding[1];
            int remainingToPackage = allocated - packaged;
            if (detail.quantity() > remainingToPackage) {
                return Either.left("Packaging quantity " + detail.quantity() + " exceeds remaining unpacked allocated quantity: " + remainingToPackage);
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateDispatch(FulfillmentLedger<String, ItemQuantity> ledger, Instant now) {
            int[] outstanding = calculateOutstandingAllocatedAndPackaged(ledger);
            int allocated = outstanding[0];
            int packaged = outstanding[1];
            if (packaged < allocated || packaged == 0) {
                return Either.left("Cannot dispatch: order is not fully packaged (packaged " + packaged + "/" + allocated + ")");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateCompletion(FulfillmentLedger<String, ItemQuantity> ledger, Instant now) {
            if (ledger.status() != FulfillmentLedger.Status.DISPATCHED) {
                return Either.left("Cannot complete: order has not been dispatched yet");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, FulfillmentLedger.Status> evaluateRelease(FulfillmentLedger<String, ItemQuantity> ledger, ItemQuantity detail, Instant now) {
            if (detail.quantity() <= 0) {
                return Either.left("Release quantity must be positive");
            }
            if (ledger.status() == FulfillmentLedger.Status.DISPATCHED || ledger.status() == FulfillmentLedger.Status.COMPLETED) {
                return Either.left("Cannot release items: order has already been dispatched or completed");
            }
            int[] outstanding = calculateOutstandingAllocatedAndPackaged(ledger);
            int allocated = outstanding[0];
            int packaged = outstanding[1];
            int maxDeductible = Math.max(allocated, packaged);
            if (detail.quantity() > maxDeductible) {
                return Either.left("Release quantity " + detail.quantity() + " exceeds active outstanding items: " + maxDeductible);
            }

            int remainingAlloc = allocated - Math.min(detail.quantity(), allocated);
            FulfillmentLedger.Status nextStatus = (remainingAlloc == 0)
                ? FulfillmentLedger.Status.INITIAL
                : ledger.status();

            return Either.right(nextStatus);
        }
    }

    // Helpers to verify outstanding quantities during testing (since ledger has no getters)
    private static int getAllocatedQuantity(FulfillmentLedger<String, ItemQuantity> ledger) {
        int allocated = 0;
        for (FulfillmentStep<ItemQuantity> step : ledger.history()) {
            switch (step.type()) {
                case ALLOCATE -> allocated += step.detail().quantity();
                case RELEASE -> allocated -= Math.min(step.detail().quantity(), allocated);
            }
        }
        return allocated;
    }

    private static int getPackagedQuantity(FulfillmentLedger<String, ItemQuantity> ledger) {
        int packaged = 0;
        for (FulfillmentStep<ItemQuantity> step : ledger.history()) {
            switch (step.type()) {
                case PACKAGE -> packaged += step.detail().quantity();
                case RELEASE -> packaged -= Math.min(step.detail().quantity(), packaged);
            }
        }
        return packaged;
    }

    // 1. Sequential Progression Invariant & Capacity Limits
    @Test
    void testFulfillmentSequentialProgression() {
        FulfillmentProcess<String, ItemQuantity> process = new FulfillmentProcess<>();
        WarehouseOrder order = new WarehouseOrder(5);
        process.register("ful-1", order).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T16:00:00Z");

        // Initiate ledger
        process.initiate("ful-1").unsafeRunSync();

        // Fails because we cannot package before allocating
        Either<String, FulfillmentLedger<String, ItemQuantity>> badPackage = process.packageItems("ful-1", "user-1", new ItemQuantity(3), "Box up keyboard", t0).unsafeRunSync();
        assertTrue(badPackage.isLeft());
        assertTrue(badPackage.getLeft().contains("Cannot package in current status"));

        // Allocate 3 (Partial allocation) -> Succeeds
        Either<String, FulfillmentLedger<String, ItemQuantity>> allocResult = process.allocate("ful-1", "user-1", new ItemQuantity(3), "Reserve stock", t0).unsafeRunSync();
        assertTrue(allocResult.isRight());
        FulfillmentLedger<String, ItemQuantity> ledger = allocResult.getRight();

        assertEquals(FulfillmentLedger.Status.ALLOCATING, ledger.status());
        assertEquals(3, getAllocatedQuantity(ledger));
        assertEquals(0, getPackagedQuantity(ledger));

        // Allocate 3 more -> Fails (total limit is 5, remaining is 2)
        Either<String, FulfillmentLedger<String, ItemQuantity>> overAlloc = process.allocate("ful-1", "user-1", new ItemQuantity(3), "Reserve extra", t0).unsafeRunSync();
        assertTrue(overAlloc.isLeft());

        // Allocate remaining 2 -> Succeeds
        Either<String, FulfillmentLedger<String, ItemQuantity>> completeAlloc = process.allocate("ful-1", "user-1", new ItemQuantity(2), "Reserve remaining", t0).unsafeRunSync();
        assertTrue(completeAlloc.isRight());
        assertEquals(5, getAllocatedQuantity(completeAlloc.getRight()));
    }

    // 2. Multi-step Fulfillment & Dispatch / Delivery Transitions
    @Test
    void testFulfillmentShipmentLifecycle() {
        InMemoryStateRepository<String, FulfillmentLedger<String, ItemQuantity>> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<FulfillmentEvent<String, ItemQuantity>> publisher = new InMemoryEventPublisher<>();
        FulfillmentProcess<String, ItemQuantity> process = new FulfillmentProcess<>(repository, publisher, new NoOpTelemetryPort());

        WarehouseOrder order = new WarehouseOrder(3);
        process.register("ful-2", order).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T16:30:00Z");

        process.initiate("ful-2").unsafeRunSync();

        // 1. Allocate 3
        process.allocate("ful-2", "user-2", new ItemQuantity(3), "Allocated screen", t0).unsafeRunSync();

        // 2. Dispatch fails because packaging is incomplete
        Either<String, FulfillmentLedger<String, ItemQuantity>> badDispatch = process.dispatch("ful-2", "user-2", "Ship to customer", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(badDispatch.isLeft());

        // 3. Package 3
        Either<String, FulfillmentLedger<String, ItemQuantity>> packageResult = process.packageItems("ful-2", "user-2", new ItemQuantity(3), "Box screen", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(packageResult.isRight());
        assertEquals(FulfillmentLedger.Status.PACKAGING, packageResult.getRight().status());
        assertEquals(3, getPackagedQuantity(packageResult.getRight()));

        // 4. Dispatch -> Succeeds (Transitions to DISPATCHED)
        Either<String, FulfillmentLedger<String, ItemQuantity>> dispatchResult = process.dispatch("ful-2", "carrier-1", "On delivery van", t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(dispatchResult.isRight());
        assertEquals(FulfillmentLedger.Status.DISPATCHED, dispatchResult.getRight().status());

        // 5. Complete delivery -> Succeeds (Transitions to COMPLETED)
        Either<String, FulfillmentLedger<String, ItemQuantity>> completeResult = process.complete("ful-2", "carrier-1", "Delivered at porch", t0.plusSeconds(40)).unsafeRunSync();
        assertTrue(completeResult.isRight());
        FulfillmentLedger<String, ItemQuantity> finalLedger = completeResult.getRight();

        assertEquals(FulfillmentLedger.Status.COMPLETED, finalLedger.status());
        assertTrue(finalLedger.isTerminal());

        // Verify Events
        List<FulfillmentEvent<String, ItemQuantity>> events = publisher.getPublishedEvents();
        assertEquals(3, events.size()); // alloc, dispatch, complete
        assertTrue(events.get(0) instanceof FulfillmentAllocated);
        assertTrue(events.get(1) instanceof FulfillmentDispatched);
        assertTrue(events.get(2) instanceof FulfillmentCompleted);
    }

    // 3. Item Release Invariant
    @Test
    void testFulfillmentRelease() {
        InMemoryStateRepository<String, FulfillmentLedger<String, ItemQuantity>> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<FulfillmentEvent<String, ItemQuantity>> publisher = new InMemoryEventPublisher<>();
        FulfillmentProcess<String, ItemQuantity> process = new FulfillmentProcess<>(repository, publisher, new NoOpTelemetryPort());

        WarehouseOrder order = new WarehouseOrder(4);
        process.register("ful-3", order).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T17:00:00Z");

        process.initiate("ful-3").unsafeRunSync();

        process.allocate("ful-3", "user-3", new ItemQuantity(4), "Allocate chairs", t0).unsafeRunSync();
        process.packageItems("ful-3", "user-3", new ItemQuantity(4), "Box chairs", t0.plusSeconds(10)).unsafeRunSync();

        // Release 2 chairs back to stock -> Succeeds
        Either<String, FulfillmentLedger<String, ItemQuantity>> releaseResult = process.release("ful-3", "user-3", new ItemQuantity(2), "Customer reduced order", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(releaseResult.isRight());
        FulfillmentLedger<String, ItemQuantity> ledger = releaseResult.getRight();

        assertEquals(FulfillmentLedger.Status.PACKAGING, ledger.status());
        assertEquals(2, getAllocatedQuantity(ledger));
        assertEquals(2, getPackagedQuantity(ledger));

        // Verify release events
        List<FulfillmentEvent<String, ItemQuantity>> events = publisher.getPublishedEvents();
        assertEquals(2, events.size()); // allocated, released
        assertTrue(events.get(1) instanceof FulfillmentReleased);
        assertEquals(2, ((FulfillmentReleased<String, ItemQuantity>) events.get(1)).detail().quantity());

        // Dispatch 2 chairs successfully
        process.dispatch("ful-3", "carrier-2", "Ship remaining", t0.plusSeconds(30)).unsafeRunSync();

        // Release fails because order is already DISPATCHED (Transit Terminal Transition!)
        Either<String, FulfillmentLedger<String, ItemQuantity>> postDispatchRelease = process.release("ful-3", "user-3", new ItemQuantity(1), "Try release in-transit", t0.plusSeconds(40)).unsafeRunSync();
        assertTrue(postDispatchRelease.isLeft());
    }
}