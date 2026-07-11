package io.effects.samples.ecommerce;

import io.effects.samples.ecommerce.domain.*;
import io.effects.samples.ecommerce.domain.models.*;
import java.time.Instant;
import java.util.List;

/**
 * Main application simulation demonstrating a fully automated B2B bulk purchasing journey, 
 * physical logistics fulfillment tracking, and post-sale corporate hardware warranty lifecycles.
 *
 * This simulation highlights how multiple corporate departments (Sales, Finance, Billing, 
 * Warehouse, Logistics, Support, and Auditing) coordinate and respond automatically to 
 * commercial events without manual step-by-step procedural calls.
 */
public class EcommerceApp {

    public static void main(String[] args) {
        DomainLogger.info("========================================================================");
        DomainLogger.info(" STARTING B2B E-COMMERCE BULK CHECKOUT & SLA LIFECYCLE SIMULATION");
        DomainLogger.info("========================================================================");

        runHappyPathSimulation();

        DomainLogger.info("\n========================================================================");
        DomainLogger.info(" SIMULATION COMPLETED SUCCESSFULLY!");
        DomainLogger.info("========================================================================");
    }

    /**
     * Executes the happy-path corporate checkout and hardware support simulation.
     * 
     * --- Business Flow Description ---
     * 1. CONTRACT NEGOTIATION: A corporate buyer negotiates pricing terms for a high-volume 
     *    purchase of developer laptops, agreeing on a custom 40% bulk discount.
     * 
     * 2. THE CORE BUSINESS TRIGGER:
     *    - Accept Terms: The buyer accepts the counter-proposed terms.
     * 
     * 3. THE AUTOMATED CHOREOGRAPHY CASCADE (Triggered by acceptTerms):
     *    - Finalized Terms accepted -> Automatically submits the pricing contract for corporate reviews.
     *    - Submittal reviewed -> The discount percentage exceeds standard policy thresholds, automatically 
     *      generating executive review tickets for the Sales VP and CFO.
     *    - CFO & VP Approve -> Executive sign-offs clear, automatically initiating a pre-authorization 
     *      credit check with the payment gateway.
     *    - Credit Authorized -> Successful payment check automatically initiates inventory stock holds 
     *      and confirms stock reservations at the regional warehouse.
     *    - Warehouse Hold Confirmed -> Stock confirmation automatically creates shipment tracking numbers 
     *      with our shipping carrier (FedEx) and schedules items at the packing station.
     *    - Items Allocated -> Shipping items are automatically boxed, labeled, and dispatched into carrier transit.
     *    - Carrier Dispatch Complete -> Package transport is tracked until signed off at the corporate destination dock.
     *    - Delivery Completed -> Dock sign-off automatically registers the physical laptop serials to the 
     *      buyer's employee accounts and activates their high-tier support Service Level Agreement (SLA).
     * 
     * 4. POST-SALE LIFECYCLE ACTIONS:
     *    - Technical support tickets are submitted and authorized under the newly granted Premium SLA.
     *    - Support session diagnostics and usage metrics are metered.
     *    - Automated cron-system schedulers run support billing settlements.
     *    - Final payment is captured and settled.
     *    - Cryptographic audits verify administrative signatures against corporate compliance standards.
     */
    public static void runHappyPathSimulation() {
        Instant t0 = Instant.parse("2026-07-09T10:00:00Z");

        // Unique business identifiers for our corporate checkout context
        String orderId = "ORDER-B2B-5544";
        String itemId = "LAPTOP-DEV-PRO-2026";
        String customerEmail = "buyer-admin@enterprise-corp.com";
        String warehouseId = "REGION-WEST-WH-1";

        DomainLogger.info("[INFO] Initializing domain behavioral systems with full end-to-end event-driven choreography...");

        // Setup the shared message communication channels representing our corporate enterprise broker
        io.effects.adapters.InMemoryEventSubscriber<Object> broker = new io.effects.adapters.InMemoryEventSubscriber<>();

        // Route various departmental events through the shared corporate broker
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

        // Initialize departmental actors and link them to the shared corporate broker
        Warehouse warehouse = new Warehouse(warehouseId, itemId, 100, reservationPublisher);

        Checkout checkout = new Checkout(orderId, broker, negotiationPublisher, approvalPublisher, paymentPublisher);
        Order order = checkout.initiateOrder(itemId, customerEmail, 50, 1500.0, broker, warehouse);

        Logistics logistics = new Logistics("FedEx & Partners", broker, fulfillmentPublisher);

        PostSaleSupport supportCycle = new PostSaleSupport(customerEmail, orderId);

        DomainLogger.info("[INFO] Domain behavioral systems initialized successfully with choreographed event channels.");

        // =====================================================================
        // --- 1. NEGOTIATE TERMS ---
        // =====================================================================
        DomainLogger.info("\n--- [STEP 1: NEGOTIATE CONTRACT TERMS] ---");
        BulkOrderTerms initialTerms = new BulkOrderTerms(50, 1500.0, 0.0);
        checkout.startNegotiation();
        checkout.proposeTerms("buyer-admin", initialTerms, t0);

        DomainLogger.info("[NEGOTIATE] Offer accepted in ledger. System counters with volume discount.");
        BulkOrderTerms counterTerms = new BulkOrderTerms(50, 1500.0, 40.0); // 40% discount
        checkout.counterPropose("sales-system", counterTerms, t0.plusSeconds(60));

        DomainLogger.info("[NEGOTIATE] Counter terms updated. Buyer accepts discounted counter terms: 40%");
        
        // --- THE CORE BUSINESS TRIGGER ---
        // Buyer accepts the contract terms. This acceptance fires a contract acceptance milestone,
        // which triggers the entire multi-departmental checkout and logistics pipeline completely automatically!
        checkout.acceptTerms("buyer-admin", t0.plusSeconds(120));

        // Allow a brief moment for the automated, multi-threaded corporate workflow cascade to fully run
        try { Thread.sleep(600); } catch (InterruptedException ignored) {}

        // Verify that the logistics courier has indeed completed and delivered the items automatically
        String finalFulfillmentStatus = logistics.getFulfillmentStatus(orderId);
        DomainLogger.info("[CHOREOGRAPHY] Auditing final logistics delivery status: " + finalFulfillmentStatus);

        // =====================================================================
        // --- 2. SERVICE LEVEL AGREEMENT (SLA) REPAIR REQUEST ---
        // =====================================================================
        // The newly delivered laptop is registered, and its premium support SLA is verified to authorize
        // repair service for a critical technical issue.
        DomainLogger.info("\n--- [STEP 2: SERVICE LEVEL AGREEMENT (SLA) REPAIR REQUEST] ---");
        order.requestSupportService("LAPTOP-DEVP-01", new SLAContext("REPAIR", 5), t0.plusSeconds(270));

        // =====================================================================
        // --- 3. PREMIUM SUPPORT USAGE METERING ---
        // =====================================================================
        // Logs diagnostic telemetry metrics to meter corporate usage of high-tier SLA services.
        DomainLogger.info("\n--- [STEP 3: PREMIUM SUPPORT USAGE METERING] ---");
        supportCycle.startSupportSession(t0.plusSeconds(280));
        supportCycle.logDiagnosticTelemetry(new DiagnosticMetric(3, 500), t0.plusSeconds(290));
        supportCycle.logDiagnosticTelemetry(new DiagnosticMetric(4, 800), t0.plusSeconds(300));
        DomainLogger.info("[METER] Support session diagnostic telemetry logged successfully.");

        // =====================================================================
        // --- 4. SCHEDULER & BILLING SETTLEMENT ---
        // =====================================================================
        // Schedulers run support usage billing runs to settle outstanding metered session costs,
        // and final payment capture is completed against the authorized total.
        DomainLogger.info("\n--- [STEP 4: SCHEDULER TRIGGER & BILLING RUN] ---");
        Instant runTime = t0.plusSeconds(3600);
        supportCycle.settleSupportBilling("cron-system", runTime, t0.plusSeconds(310));

        double totalPrice = 50 * 1500.0 * 0.60; // 50 Laptops with 40% discount = $45,000
        checkout.capturePayment("buyer-admin", totalPrice, "Capture full amount upon warehouse dispatch.", runTime);

        // =====================================================================
        // --- 5. COMPLIANCE SECURITY AUDITING ---
        // =====================================================================
        // Audits the cryptographic signatures of administrative approvals to verify compliance
        // with corporate purchasing policies.
        DomainLogger.info("\n--- [STEP 5: COMPLIANCE SECURITY AUDITING] ---");
        List<AuditEntry> administrativeTrail = List.of(
            new AuditEntry("INITIATED", "buyer-admin", "SIGNATURE-MD5"),
            new AuditEntry("DISCOUNT_APPROVED_VP", "vp-sarah", "SIGNATURE-SHA256"),
            new AuditEntry("DISCOUNT_APPROVED_CFO", "cfo-steve", "SIGNATURE-SHA256-B")
        );
        supportCycle.verifySecurityCompliance("buyer-admin", administrativeTrail, runTime);
    }
}
