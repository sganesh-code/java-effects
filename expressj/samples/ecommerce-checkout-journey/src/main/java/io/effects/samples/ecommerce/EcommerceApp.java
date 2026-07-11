package io.effects.samples.ecommerce;

import io.effects.samples.ecommerce.domain.*;
import io.effects.samples.ecommerce.domain.models.*;
import java.time.Instant;
import java.util.List;

public class EcommerceApp {

    public static void main(String[] args) {
        DomainLogger.info("========================================================================");
        DomainLogger.info(" STARTING B2B E-COMMERCE BULK CHECKOUT & SLA LIFECYCLE SIMULATION");
        DomainLogger.info("========================================================================");

        // Run our happy-path simulation flow
        runHappyPathSimulation();

        DomainLogger.info("\n========================================================================");
        DomainLogger.info(" SIMULATION COMPLETED SUCCESSFULLY!");
        DomainLogger.info("========================================================================");
    }

    public static void runHappyPathSimulation() {
        Instant t0 = Instant.parse("2026-07-09T10:00:00Z");

        // IDs for our entities
        String orderId = "ORDER-B2B-5544";
        String itemId = "LAPTOP-DEV-PRO-2026";
        String customerEmail = "buyer-admin@enterprise-corp.com";
        String warehouseId = "REGION-WEST-WH-1";

        DomainLogger.info("[INFO] Initializing domain behavioral systems with event-driven choreography...");

        // Create our shared in-memory pub/sub broker
        io.effects.adapters.InMemoryEventSubscriber<Object> broker = new io.effects.adapters.InMemoryEventSubscriber<>();

        // Aligned type-safe publishers that delegate to the broker
        io.effects.ports.EventPublisher<io.effects.recipes.reservable.ReservationEvent<String, Integer>> reservationPublisher = event -> 
            broker.publish(event.getClass().getSimpleName(), event);
            
        io.effects.ports.EventPublisher<io.effects.recipes.fulfillable.FulfillmentEvent<String, Integer>> fulfillmentPublisher = event -> 
            broker.publish(event.getClass().getSimpleName(), event);

        // 1. Initialize our first-class domain behavioral objects with explicit capacities/identities
        Warehouse warehouse = new Warehouse(warehouseId, itemId, 100, reservationPublisher);

        CheckoutJourney checkout = new CheckoutJourney(orderId);
        Order order = checkout.initiateOrder(itemId, customerEmail, 50, 1500.0, broker);

        LogisticsProvider logistics = new LogisticsProvider("FedEx & Partners", broker, fulfillmentPublisher);

        PostSaleSupportCycle supportCycle = new PostSaleSupportCycle(customerEmail, orderId);

        DomainLogger.info("[INFO] Domain behavioral systems initialized successfully with event-driven channels.");

        // --- 1. NEGOTIATE TERMS ---
        DomainLogger.info("\n--- [STEP 1: NEGOTIATE TERMS] ---");
        BulkOrderTerms initialTerms = new BulkOrderTerms(50, 1500.0, 0.0);
        checkout.startNegotiation();
        checkout.proposeTerms("buyer-admin", initialTerms, t0);

        DomainLogger.info("[NEGOTIATE] Offer accepted in ledger. System counters with volume discount.");
        BulkOrderTerms counterTerms = new BulkOrderTerms(50, 1500.0, 40.0); // 40% discount
        checkout.counterPropose("sales-system", counterTerms, t0.plusSeconds(60));

        DomainLogger.info("[NEGOTIATE] Counter terms updated. Buyer accepts discounted counter terms: 40%");
        checkout.acceptTerms("buyer-admin", t0.plusSeconds(120));

        // --- 2. MULTI-LEVEL APPROVAL FLOW ---
        DomainLogger.info("\n--- [STEP 2: MULTI-LEVEL APPROVAL FLOW] ---");
        checkout.submitForDiscountApproval("sales-rep", 40.0, "Bulk contract for 50 Laptops at 40% discount", t0.plusSeconds(130));
        checkout.approveDiscount("vp-sarah", "SALES_VP", "Approved volume discount - strategic corporate account.", t0.plusSeconds(140));
        checkout.approveDiscount("cfo-steve", "CFO", "Financially approved.", t0.plusSeconds(150));

        // --- 3. PAYMENT AUTHORIZATION ---
        DomainLogger.info("\n--- [STEP 3: INITIAL PAYMENT AUTHORIZATION] ---");
        double totalPrice = 50 * 1500.0 * 0.60; // 50 Laptops at 40% discount = $45,000
        checkout.authorizePayment("buyer-admin", totalPrice, t0.plusSeconds(160));

        // --- 4. INVENTORY RESERVATION & AUTOMATIC SHIPMENT INITIATION ---
        // Pass orderId as the actor reserving the stock to allow HoldConfirmed event to carry the orderId to LogisticsProvider
        order.reserveStock(warehouse, orderId, 3600, t0.plusSeconds(170));
        
        DomainLogger.info("\n--- [STEP 5: LOGISTICS SHIPPING FULFILLMENT VIA CHOREOGRAPHY] ---");
        order.confirmStock(warehouse, t0.plusSeconds(200)); 
        // Note: confirming stock emits a HoldConfirmed event which is caught by LogisticsProvider, 
        // asynchronously initiating shipment and allocating items in our e-commerce choreography!

        // Wait a small bit for virtual-thread choreography propagation
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        order.packageShippingItems(logistics, "logistics-bot", "Boxed and labeled.", t0.plusSeconds(220));
        order.dispatchShipment(logistics, "carrier-fedex", "Dispatched via FedEx Express", t0.plusSeconds(230));
        
        DomainLogger.info("\n--- [STEP 6: DELIVERY COMPLETION & AUTOMATIC WARRANTY/OWNERSHIP VIA CHOREOGRAPHY] ---");
        order.completeDelivery(logistics, "buyer-admin", "Delivered and signed at corporate dock.", t0.plusSeconds(240));
        // Note: completeDelivery executes delivery completion on LogisticsProvider, which emits a FulfillmentCompleted event.
        // AssetRegistry catches FulfillmentCompleted, automatically registering asset ownership and granting the premium warranty!

        // Wait a small bit for virtual-thread choreography propagation
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        // --- 7. SERVICE LEVEL AGREEMENT (SLA) REPAIR REQUEST ---
        order.requestSupportService("LAPTOP-DEVP-01", new SLAContext("REPAIR", 5), t0.plusSeconds(270));

        // --- 8. PREMIUM SUPPORT USAGE METERING ---
        DomainLogger.info("\n--- [STEP 8: PREMIUM SUPPORT USAGE METERING] ---");
        supportCycle.startSupportSession(t0.plusSeconds(280));
        supportCycle.logDiagnosticTelemetry(new DiagnosticMetric(3, 500), t0.plusSeconds(290));
        supportCycle.logDiagnosticTelemetry(new DiagnosticMetric(4, 800), t0.plusSeconds(300));
        DomainLogger.info("[METER] Support telemetry logged successfully.");

        // --- 9. SCHEDULER & BILLING SETTLEMENT ---
        DomainLogger.info("\n--- [STEP 9: SCHEDULER TRIGGER & BILLING RUN] ---");
        Instant runTime = t0.plusSeconds(3600);
        supportCycle.settleSupportBilling("cron-system", runTime, t0.plusSeconds(310));

        checkout.capturePayment("buyer-admin", totalPrice, "Capture full amount upon warehouse dispatch.", runTime);

        // --- 10. COMPLIANCE SECURITY AUDITING ---
        DomainLogger.info("\n--- [STEP 10: CRYPTOGRAPHIC COMPLIANCE AUDITING] ---");
        List<AuditEntry> administrativeTrail = List.of(
            new AuditEntry("INITIATED", "buyer-admin", "SIGNATURE-MD5"),
            new AuditEntry("DISCOUNT_APPROVED_VP", "vp-sarah", "SIGNATURE-SHA256"),
            new AuditEntry("DISCOUNT_APPROVED_CFO", "cfo-steve", "SIGNATURE-SHA256-B")
        );
        supportCycle.verifySecurityCompliance("buyer-admin", administrativeTrail, runTime);
    }
}
