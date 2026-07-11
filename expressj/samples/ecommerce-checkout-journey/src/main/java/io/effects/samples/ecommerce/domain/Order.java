package io.effects.samples.ecommerce.domain;

import io.effects.IO;
import io.effects.ports.EventSubscriber;
import io.effects.recipes.reservable.Hold;
import io.effects.samples.ecommerce.domain.models.SLAContext;
import io.effects.samples.ecommerce.domain.models.WarrantyGrant;
import java.time.Instant;

/**
 * Represents a customer purchase order. 
 * It manages the required purchase details, buyer references, and quantity, and coordinates 
 * securing the inventory stock as well as managing post-sale warranty support.
 */
public class Order {
    private final String itemId;
    private final String customerEmail;
    private final int quantity;
    private Hold<String, Integer> stockHold;
    private final AssetRegistry assetRegistry;
    private final EventSubscriber<Object> subscriberPort;
    private final Warehouse warehouse;

    /**
     * Creates a new purchase order with a specific item identifier, buyer email address, 
     * purchase quantity, and unit price.
     */
    public Order(String itemId, String customerEmail, int quantity, double unitPrice, EventSubscriber<Object> subscriberPort, Warehouse warehouse) {
        this.itemId = itemId;
        this.customerEmail = customerEmail;
        this.quantity = quantity;
        this.subscriberPort = subscriberPort;
        this.warehouse = warehouse;
        this.assetRegistry = new AssetRegistry(subscriberPort, customerEmail);
        if (subscriberPort != null) {
            setupOrderTriggers();
        }
    }

    /**
     * Configures automatic inventory triggers to coordinate warehouse processes when 
     * preceding checkout checkpoints are reached.
     */
    private void setupOrderTriggers() {
        // Automatically reserve and confirm stock once payment credit has been successfully authorized
        subscriberPort.subscribe("PaymentAuthorized", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.payable.PaymentAuthorized<?, ?> event) {
                String ordId = event.paymentId().toString();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[ORDER] Payment successfully authorized. Automatically securing warehouse inventory holds for order: " + ordId);
                
                if (warehouse != null) {
                    reserveStock(warehouse, ordId, 3600, now.plusSeconds(10));
                    confirmStock(warehouse, now.plusSeconds(20));
                }
            }
            return null;
        })).unsafeRunSync();
    }

    public void applyNegotiatedDiscount(double discountPercentage) {
        // Update the contract value to apply the approved discount percentage.
    }

    // --- Core Purchasing & Support Operations ---

    /**
     * Secures a temporary inventory hold for the items on this order.
     */
    public void reserveStock(Warehouse warehouse, String orderId, int ttlSeconds, Instant time) {
        DomainLogger.info("[ORDER] Initiating temporary inventory stock check and hold...");
        this.stockHold = warehouse.reserveStock(itemId, orderId, quantity, ttlSeconds, time);
    }

    /**
     * Finalizes the temporary stock hold, moving the items to the shipping queue.
     */
    public void confirmStock(Warehouse warehouse, Instant time) {
        if (this.stockHold == null) {
            throw new IllegalStateException("Cannot confirm inventory stock allocation: no active temporary stock hold exists.");
        }
        warehouse.confirmStock(stockHold.holdId(), time);
    }

    /**
     * Requests a post-sale technical repair or maintenance support session for a device 
     * covered under the order's service level agreement.
     */
    public void requestSupportService(String deviceId, SLAContext requestContext, Instant time) {
        DomainLogger.info("[ORDER] Buyer requests support service session...");
        WarrantyGrant premiumGrant = new WarrantyGrant(deviceId, "PREMIUM");
        assetRegistry.checkSLAAuthorization(customerEmail, premiumGrant, requestContext, time);
    }
}
