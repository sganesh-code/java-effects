package io.effects.samples.ecommerce.domain;

import io.effects.Either;
import io.effects.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.EventSubscriber;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.fulfillable.*;
import java.time.Instant;

/**
 * First-class business object representing a logistics and shipping fulfillment provider.
 * It encapsulates and directly implements the Fulfillable recipe process.
 */
public class Logistics implements FulfillableRequest<String, Integer> {
    private final String providerName;
    private final FulfillmentProcess<String, Integer> shippingProcess;
    private final EventSubscriber<Object> subscriberPort;

    public Logistics(String providerName, EventSubscriber<Object> subscriberPort, EventPublisher<FulfillmentEvent<String, Integer>> publisher) {
        this.providerName = providerName;
        this.shippingProcess = new FulfillmentProcess<>(new InMemoryStateRepository<>(), publisher, new NoOpTelemetryPort());
        this.subscriberPort = subscriberPort;
        if (subscriberPort != null) {
            subscribeToEvents();
        }
    }

    public Logistics(String providerName, EventSubscriber<Object> subscriberPort) {
        this(providerName, subscriberPort, new InMemoryEventPublisher<>());
    }

    public Logistics(String providerName) {
        this(providerName, null, new InMemoryEventPublisher<>());
    }

    private void subscribeToEvents() {
        // 1. HoldConfirmed -> initiate and allocate
        subscriberPort.subscribe("HoldConfirmed", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.reservable.HoldConfirmed<?, ?> event) {
                String orderId = event.actorId();
                String actorId = "buyer-admin";
                int qty = ((Number) event.quantity()).intValue();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[CHOREOGRAPHY] LogisticsProvider caught HoldConfirmed event. Asynchronously orchestrating shipment for: " + orderId);
                
                // Choreographed side-effects triggered automatically
                initiateShipment(orderId);
                allocateItems(orderId, actorId, qty, "Asynchronously allocated items via HoldConfirmed choreography", now.plusSeconds(5));
            }
            return null;
        })).unsafeRunSync();

        // 2. FulfillmentAllocated -> package and dispatch
        subscriberPort.subscribe("FulfillmentAllocated", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.fulfillable.FulfillmentAllocated<?, ?> event) {
                String orderId = event.fulfillmentId().toString();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[CHOREOGRAPHY] LogisticsProvider caught FulfillmentAllocated event. Asynchronously packaging and dispatching: " + orderId);
                
                // Choreographed packaging and dispatch steps triggered automatically
                packageItems(orderId, "logistics-bot", 50, "Boxed and labeled via automated choreography", now.plusSeconds(5));
                dispatchShipment(orderId, "carrier-fedex", "Dispatched via FedEx Express via automated choreography", now.plusSeconds(10));
            }
            return null;
        })).unsafeRunSync();

        // 3. FulfillmentDispatched -> complete delivery
        subscriberPort.subscribe("FulfillmentDispatched", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.fulfillable.FulfillmentDispatched<?, ?> event) {
                String orderId = event.fulfillmentId().toString();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[CHOREOGRAPHY] LogisticsProvider caught FulfillmentDispatched event. Asynchronously completing delivery for: " + orderId);
                
                // Choreographed delivery completion triggered automatically
                completeDelivery(orderId, "buyer-admin", "Delivered and signed at corporate dock via automated choreography", now.plusSeconds(5));
            }
            return null;
        })).unsafeRunSync();
    }

    // --- High-Level Business Behaviors ---

    public void initiateShipment(String orderId) {
        DomainLogger.info("[LOGISTICS] Initiating shipping fulfillment package tracking via: " + providerName);
        shippingProcess.register(orderId, this).unsafeRunSync();
        shippingProcess.initiate(orderId).unsafeRunSync();
    }

    public void allocateItems(String orderId, String actorId, int quantity, String comment, Instant time) {
        var res = shippingProcess.allocate(orderId, actorId, quantity, comment, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Logistics allocation failed: " + res.getLeft());
        }
        DomainLogger.info("[LOGISTICS] Item quantities allocated. Current status: " + res.getRight().status());
    }

    public void packageItems(String orderId, String actorId, int quantity, String comment, Instant time) {
        var res = shippingProcess.packageItems(orderId, actorId, quantity, comment, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Logistics packaging failed: " + res.getLeft());
        }
        DomainLogger.info("[LOGISTICS] Items boxed. Status: " + res.getRight().status());
    }

    public void dispatchShipment(String orderId, String carrierId, String comment, Instant time) {
        var res = shippingProcess.dispatch(orderId, carrierId, comment, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Logistics dispatch failed: " + res.getLeft());
        }
        DomainLogger.info("[LOGISTICS] Shipped in-transit! Status: " + res.getRight().status());
    }

    public void completeDelivery(String orderId, String actorId, String comment, Instant time) {
        var res = shippingProcess.complete(orderId, actorId, comment, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Logistics completion failed: " + res.getLeft());
        }
        DomainLogger.info("[SUCCESS] Delivery completed via " + providerName + "! Status: " + res.getRight().status());
    }

    // --- FulfillableRequest Interface Implementation ---

    @Override
    public Either<String, Void> evaluateAllocation(FulfillmentLedger<String, Integer> ledger, Integer quantity, Instant now) {
        if (quantity <= 0) {
            return Either.left("Allocation quantity must be positive.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluatePackaging(FulfillmentLedger<String, Integer> ledger, Integer quantity, Instant now) {
        if (ledger.status() != FulfillmentLedger.Status.ALLOCATING) {
            return Either.left("Items must be allocated before they can be packaged.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateDispatch(FulfillmentLedger<String, Integer> ledger, Instant now) {
        if (ledger.status() != FulfillmentLedger.Status.PACKAGING) {
            return Either.left("Items must be packaged before they can be dispatched.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateCompletion(FulfillmentLedger<String, Integer> ledger, Instant now) {
        if (ledger.status() != FulfillmentLedger.Status.DISPATCHED) {
            return Either.left("Fulfillment must be dispatched before it can be completed.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, FulfillmentLedger.Status> evaluateRelease(FulfillmentLedger<String, Integer> ledger, Integer quantity, Instant now) {
        if (ledger.status() == FulfillmentLedger.Status.DISPATCHED || ledger.status() == FulfillmentLedger.Status.COMPLETED) {
            return Either.left("Cannot release items once shipped or completed.");
        }
        return Either.right(FulfillmentLedger.Status.INITIAL);
    }

    /**
     * Queries the final state of the fulfillment ledger.
     */
    public String getFulfillmentStatus(String orderId) {
        var optLedger = shippingProcess.find(orderId).unsafeRunSync();
        return optLedger.map(ledger -> ledger.status().toString()).orElse("UNKNOWN");
    }
}
