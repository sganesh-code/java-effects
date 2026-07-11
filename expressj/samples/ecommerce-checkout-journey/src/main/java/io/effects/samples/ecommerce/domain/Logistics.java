package io.effects.samples.ecommerce.domain;

import io.effects.Either;
import io.effects.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.EventSubscriber;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.fulfillable.*;
import java.time.Instant;

/**
 * Represents a shipping and logistics fulfillment provider (such as FedEx or DHL). 
 * It manages the lifecycle of physical packages: from packing station allocation and carrier dispatching
 * to transit tracking and corporate destination delivery confirmation.
 */
public class Logistics implements FulfillableRequest<String, Integer> {
    private final String providerName;
    private final FulfillmentProcess<String, Integer> shippingProcess;
    private final EventSubscriber<Object> subscriberPort;

    /**
     * Initializes a logistics provider with an explicit carrier name, a shared message subscription channel, 
     * and a shared event publication channel.
     */
    public Logistics(String providerName, EventSubscriber<Object> subscriberPort, EventPublisher<FulfillmentEvent<String, Integer>> publisher) {
        this.providerName = providerName;
        this.shippingProcess = new FulfillmentProcess<>(new InMemoryStateRepository<>(), publisher, new NoOpTelemetryPort());
        this.subscriberPort = subscriberPort;
        if (subscriberPort != null) {
            subscribeToEvents();
        }
    }

    /**
     * Set up automated shipping triggers in response to purchase order status milestones.
     */
    private void subscribeToEvents() {
        // 1. Stock hold confirmed -> automatically initiate tracking and allocate stock
        subscriberPort.subscribe("HoldConfirmed", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.reservable.HoldConfirmed<?, ?> event) {
                String orderId = event.actorId();
                String actorId = "buyer-admin";
                int qty = ((Number) event.quantity()).intValue();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[LOGISTICS] Warehouse confirmed stock availability. Automatically orchestrating shipping setup for order: " + orderId);
                
                initiateShipment(orderId);
                allocateItems(orderId, actorId, qty, "Asynchronously allocated items via automated order workflow", now.plusSeconds(5));
            }
            return null;
        })).unsafeRunSync();

        // 2. Items allocated -> automatically box items and hand over to dispatch carrier
        subscriberPort.subscribe("FulfillmentAllocated", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.fulfillable.FulfillmentAllocated<?, ?> event) {
                String orderId = event.fulfillmentId().toString();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[LOGISTICS] Order items allocated at packing station. Automatically packaging and dispatching: " + orderId);
                
                packageItems(orderId, "logistics-bot", 50, "Boxed and labeled via automated packing robot", now.plusSeconds(5));
                dispatchShipment(orderId, "carrier-fedex", "Dispatched via FedEx Express courier", now.plusSeconds(10));
            }
            return null;
        })).unsafeRunSync();

        // 3. Carrier dispatch completed -> automatically track transit and complete delivery
        subscriberPort.subscribe("FulfillmentDispatched", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.fulfillable.FulfillmentDispatched<?, ?> event) {
                String orderId = event.fulfillmentId().toString();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[LOGISTICS] Shipment handed over to carrier. Automatically completing delivery at corporate dock for: " + orderId);
                
                completeDelivery(orderId, "buyer-admin", "Delivered and signed at corporate receiving dock", now.plusSeconds(5));
            }
            return null;
        })).unsafeRunSync();
    }

    // --- Core Shipping & Fulfillment Operations ---

    /**
     * Establishes a formal tracking registry for a shipping package.
     */
    public void initiateShipment(String orderId) {
        DomainLogger.info("[LOGISTICS] Generating shipment tracking number under carrier: " + providerName);
        shippingProcess.register(orderId, this).unsafeRunSync();
        shippingProcess.initiate(orderId).unsafeRunSync();
    }

    /**
     * Reserves and brings physical item quantities to the warehouse packing station.
     */
    public void allocateItems(String orderId, String actorId, int quantity, String comment, Instant time) {
        var res = shippingProcess.allocate(orderId, actorId, quantity, comment, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Logistics allocation failed: " + res.getLeft());
        }
        DomainLogger.info("[LOGISTICS] Shipment item quantities allocated. Current status: " + res.getRight().status());
    }

    /**
     * Boxes and labels items for secure transport.
     */
    public void packageItems(String orderId, String actorId, int quantity, String comment, Instant time) {
        var res = shippingProcess.packageItems(orderId, actorId, quantity, comment, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Logistics packaging failed: " + res.getLeft());
        }
        DomainLogger.info("[LOGISTICS] Shipment items boxed. Status: " + res.getRight().status());
    }

    /**
     * Dispatches the package onto carrier transit routes.
     */
    public void dispatchShipment(String orderId, String carrierId, String comment, Instant time) {
        var res = shippingProcess.dispatch(orderId, carrierId, comment, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Logistics dispatch failed: " + res.getLeft());
        }
        DomainLogger.info("[LOGISTICS] Shipment in transit! Status: " + res.getRight().status());
    }

    /**
     * Signs off delivery receipt at corporate dock destination.
     */
    public void completeDelivery(String orderId, String actorId, String comment, Instant time) {
        var res = shippingProcess.complete(orderId, actorId, comment, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Logistics completion failed: " + res.getLeft());
        }
        DomainLogger.info("[LOGISTICS] Package successfully signed and delivered via " + providerName + "! Status: " + res.getRight().status());
    }

    /**
     * Queries the final state of shipping fulfillment.
     */
    public String getFulfillmentStatus(String orderId) {
        var optLedger = shippingProcess.find(orderId).unsafeRunSync();
        return optLedger.map(ledger -> ledger.status().toString()).orElse("UNKNOWN");
    }

    // --- Core Fulfillment Assessment Policies ---

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
            return Either.left("Items must be allocated at packing station before boxing.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateDispatch(FulfillmentLedger<String, Integer> ledger, Instant now) {
        if (ledger.status() != FulfillmentLedger.Status.PACKAGING) {
            return Either.left("Items must be boxed and labeled before dispatch handover.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateCompletion(FulfillmentLedger<String, Integer> ledger, Instant now) {
        if (ledger.status() != FulfillmentLedger.Status.DISPATCHED) {
            return Either.left("Shipment must be in-transit before delivery can be completed.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, FulfillmentLedger.Status> evaluateRelease(FulfillmentLedger<String, Integer> ledger, Integer quantity, Instant now) {
        if (ledger.status() == FulfillmentLedger.Status.DISPATCHED || ledger.status() == FulfillmentLedger.Status.COMPLETED) {
            return Either.left("Cannot cancel shipping package once dispatched or delivered.");
        }
        return Either.right(FulfillmentLedger.Status.INITIAL);
    }
}
