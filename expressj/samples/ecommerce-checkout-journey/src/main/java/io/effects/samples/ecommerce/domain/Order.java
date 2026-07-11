package io.effects.samples.ecommerce.domain;

import io.effects.IO;
import io.effects.ports.EventSubscriber;
import io.effects.recipes.reservable.Hold;
import io.effects.samples.ecommerce.domain.models.SLAContext;
import io.effects.samples.ecommerce.domain.models.WarrantyGrant;
import java.time.Instant;

public class Order {
    private final String orderId;
    private final String itemId;
    private final String customerEmail;
    private final int quantity;
    private String status;
    
    // Encapsulated states
    private Hold<String, Integer> stockHold;
    private final AssetRegistry assetRegistry;
    private final EventSubscriber<Object> subscriberPort;
    private final Warehouse warehouse;

    public Order(String orderId, String itemId, String customerEmail, int quantity, double unitPrice, EventSubscriber<Object> subscriberPort, Warehouse warehouse) {
        this.orderId = orderId;
        this.itemId = itemId;
        this.customerEmail = customerEmail;
        this.quantity = quantity;
        this.status = "CREATED";
        this.subscriberPort = subscriberPort;
        this.warehouse = warehouse;
        this.assetRegistry = new AssetRegistry(subscriberPort, customerEmail);
        if (subscriberPort != null) {
            subscribeToEvents();
        }
    }

    public Order(String orderId, String itemId, String customerEmail, int quantity, double unitPrice, EventSubscriber<Object> subscriberPort) {
        this(orderId, itemId, customerEmail, quantity, unitPrice, subscriberPort, null);
    }

    public Order(String orderId, String itemId, String customerEmail, int quantity, double unitPrice) {
        this(orderId, itemId, customerEmail, quantity, unitPrice, null, null);
    }

    private void subscribeToEvents() {
        subscriberPort.subscribe("PaymentAuthorized", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.payable.PaymentAuthorized<?, ?> event) {
                String ordId = event.paymentId().toString();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[CHOREOGRAPHY] Order caught PaymentAuthorized event. Asynchronously reserving inventory stock for order: " + ordId);
                
                if (warehouse != null) {
                    // Choreographed side-effect: Reserve inventory and confirm stock hold!
                    reserveStock(warehouse, ordId, 3600, now.plusSeconds(10));
                    confirmStock(warehouse, now.plusSeconds(20));
                }
            }
            return null;
        })).unsafeRunSync();
    }

    public void applyNegotiatedDiscount(double discountPercentage) {
        this.status = "DISCOUNT_APPLIED";
    }

    // --- Active Behavioral Methods (Fulfillment Coordination) ---

    public void reserveStock(Warehouse warehouse, String actorId, int ttlSeconds, Instant time) {
        DomainLogger.info("\n--- [STEP 4: WAREHOUSE INVENTORY RESERVATION] ---");
        this.stockHold = warehouse.reserveStock(itemId, actorId, quantity, ttlSeconds, time);
        this.status = "STOCK_HELD";
    }

    public void confirmStock(Warehouse warehouse, Instant time) {
        if (this.stockHold == null) {
            throw new IllegalStateException("Cannot confirm stock: no active stock hold exists for this order.");
        }
        warehouse.confirmStock(stockHold.holdId(), time);
        this.status = "STOCK_CONFIRMED";
    }

    public void initiateShipment(LogisticsProvider logistics) {
        DomainLogger.info("\n--- [STEP 5: LOGISTICS SHIPPING FULFILLMENT] ---");
        logistics.initiateShipment(orderId);
    }

    public void allocateShippingItems(LogisticsProvider logistics, String actorId, String comment, Instant time) {
        logistics.allocateItems(orderId, actorId, quantity, comment, time);
    }

    public void packageShippingItems(LogisticsProvider logistics, String actorId, String comment, Instant time) {
        logistics.packageItems(orderId, actorId, quantity, comment, time);
    }

    public void dispatchShipment(LogisticsProvider logistics, String carrierId, String comment, Instant time) {
        logistics.dispatchShipment(orderId, carrierId, comment, time);
        this.status = "SHIPPED";
    }

    public void completeDelivery(LogisticsProvider logistics, String actorId, String comment, Instant time) {
        logistics.completeDelivery(orderId, actorId, comment, time);
        this.status = "DELIVERED";
    }

    public void requestSupportService(String deviceId, SLAContext requestContext, Instant time) {
        DomainLogger.info("\n--- [STEP 7: SERVICE LEVEL AGREEMENT (SLA) REPAIR REQUEST] ---");
        WarrantyGrant premiumGrant = new WarrantyGrant(deviceId, "PREMIUM");
        assetRegistry.checkSLAAuthorization(customerEmail, premiumGrant, requestContext, time);
    }
}
