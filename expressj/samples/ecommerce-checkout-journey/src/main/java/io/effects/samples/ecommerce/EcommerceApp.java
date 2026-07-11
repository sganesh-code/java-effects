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

        DomainLogger.info("[INFO] Initializing domain behavioral systems with full end-to-end event-driven choreography...");

        // Create our shared in-memory pub/sub broker
        io.effects.adapters.InMemoryEventSubscriber<Object> broker = new io.effects.adapters.InMemoryEventSubscriber<>();

        // Aligned type-safe publishers that delegate to the broker
        io.effects.ports.EventPublisher<io.effects.recipes.negotiable.NegotiationEvent<String>> negotiationPublisher = event -> 
            broker.publish(event.getClass().getSimpleName(), event);

        io.effects.ports.EventPublisher<io.effects.recipes.approvable.ApprovalEvent<String, String>> approvalPublisher = event -> 
            broker.publish(event.getClass().getSimpleName(), event);

        io.effects.ports.EventPublisher<io.effects.recipes.payable.PaymentEvent<String, Double>> paymentPublisher = event -> 
            broker.publish(event.getClass().getSimpleName(), event);

        io.effects.ports.EventPublisher<io.effects.recipes.reservable.ReservationEvent<String, Integer>> reservationPublisher = event -> 
            broker.publish(event.getClass().getSimpleName(), event);
            
        io.effects.ports.EventPublisher<io.effects.recipes.fulfillable.FulfillmentEvent<String, Integer>> fulfillmentPublisher = event -> 
            broker.publish(event.getClass().getSimpleName(), event);

        // 1. Initialize our first-class domain behavioral objects with explicit capacities/identities
        Warehouse warehouse = new Warehouse(warehouseId, itemId, 100, reservationPublisher);

        CheckoutJourney checkout = new CheckoutJourney(orderId, broker, negotiationPublisher, approvalPublisher, paymentPublisher);
        Order order = checkout.initiateOrder(itemId, customerEmail, 50, 1500.0, broker, warehouse);

        LogisticsProvider logistics = new LogisticsProvider("FedEx & Partners", broker, fulfillmentPublisher);

        PostSaleSupportCycle supportCycle = new PostSaleSupportCycle(customerEmail, orderId);

        DomainLogger.info("[INFO] Domain behavioral systems initialized successfully with choreographed event channels.");

        // --- 1. NEGOTIATE TERMS ---
        DomainLogger.info("\n--- [STEP 1: NEGOTIATE TERMS] ---");
        BulkOrderTerms initialTerms = new BulkOrderTerms(50, 1500.0, 0.0);
        checkout.startNegotiation();
        checkout.proposeTerms("buyer-admin", initialTerms, t0);

        DomainLogger.info("[NEGOTIATE] Offer accepted in ledger. System counters with volume discount.");
        BulkOrderTerms counterTerms = new BulkOrderTerms(50, 1500.0, 40.0); // 40% discount
        checkout.counterPropose("sales-system", counterTerms, t0.plusSeconds(60));

        DomainLogger.info("[NEGOTIATE] Counter terms updated. Buyer accepts discounted counter terms: 40%");
        
        // This accepts the terms, emitting a NegotiationAccepted event!
        // This NegotiationAccepted event triggers:
        // -> submitForDiscountApproval (caught by DiscountApprovalWorkflow) -> emits RequestSubmitted (VP)
        // -> Auto-VP Approve (caught by DiscountApprovalWorkflow) -> emits RequestSubmitted (CFO)
        // -> Auto-CFO Approve (caught by DiscountApprovalWorkflow) -> emits RequestApproved
        // -> Auto-Authorize Payment (caught by OrderPaymentTransaction) -> emits PaymentAuthorized
        // -> Auto-Reserve & Confirm Stock (caught by Order) -> emits HoldConfirmed
        // -> Auto-Initiate Shipment & Allocate (caught by LogisticsProvider) -> emits FulfillmentAllocated
        // -> Auto-Package & Dispatch Shipping (caught by LogisticsProvider) -> emits FulfillmentDispatched
        // -> Auto-Complete Delivery (caught by LogisticsProvider) -> emits FulfillmentCompleted
        // -> Auto-Register Ownership & SLA Warranty (caught by AssetRegistry) -> completes end-to-end checkout!
        checkout.acceptTerms("buyer-admin", t0.plusSeconds(120));

        // Wait a small bit for virtual-thread choreography propagation
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        // --- 2. SERVICE LEVEL AGREEMENT (SLA) REPAIR REQUEST ---
        order.requestSupportService("LAPTOP-DEVP-01", new SLAContext("REPAIR", 5), t0.plusSeconds(270));

        // --- 3. PREMIUM SUPPORT USAGE METERING ---
        DomainLogger.info("\n--- [STEP 3: PREMIUM SUPPORT USAGE METERING] ---");
        supportCycle.startSupportSession(t0.plusSeconds(280));
        supportCycle.logDiagnosticTelemetry(new DiagnosticMetric(3, 500), t0.plusSeconds(290));
        supportCycle.logDiagnosticTelemetry(new DiagnosticMetric(4, 800), t0.plusSeconds(300));
        DomainLogger.info("[METER] Support telemetry logged successfully.");

        // --- 4. SCHEDULER & BILLING SETTLEMENT ---
        DomainLogger.info("\n--- [STEP 4: SCHEDULER TRIGGER & BILLING RUN] ---");
        Instant runTime = t0.plusSeconds(3600);
        supportCycle.settleSupportBilling("cron-system", runTime, t0.plusSeconds(310));

        double totalPrice = 50 * 1500.0 * 0.60;
        checkout.capturePayment("buyer-admin", totalPrice, "Capture full amount upon warehouse dispatch.", runTime);

        // --- 5. COMPLIANCE SECURITY AUDITING ---
        DomainLogger.info("\n--- [STEP 5: CRYPTOGRAPHIC COMPLIANCE AUDITING] ---");
        List<AuditEntry> administrativeTrail = List.of(
            new AuditEntry("INITIATED", "buyer-admin", "SIGNATURE-MD5"),
            new AuditEntry("DISCOUNT_APPROVED_VP", "vp-sarah", "SIGNATURE-SHA256"),
            new AuditEntry("DISCOUNT_APPROVED_CFO", "cfo-steve", "SIGNATURE-SHA256-B")
        );
        supportCycle.verifySecurityCompliance("buyer-admin", administrativeTrail, runTime);
    }
}
