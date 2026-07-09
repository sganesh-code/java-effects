package io.effects.samples.ecommerce;

import io.effects.Either;
import io.effects.recipes.negotiable.*;
import io.effects.recipes.approvable.*;
import io.effects.recipes.payable.*;
import io.effects.recipes.reservable.*;
import io.effects.recipes.fulfillable.*;
import io.effects.recipes.ownable.*;
import io.effects.recipes.entitleable.*;
import io.effects.recipes.meterable.*;
import io.effects.recipes.auditable.*;
import io.effects.recipes.schedulable.*;
import io.effects.samples.ecommerce.negotiable.*;
import io.effects.samples.ecommerce.approvable.*;
import io.effects.samples.ecommerce.payable.*;
import io.effects.samples.ecommerce.reservable.*;
import io.effects.samples.ecommerce.fulfillable.*;
import io.effects.samples.ecommerce.ownable.*;
import io.effects.samples.ecommerce.entitleable.*;
import io.effects.samples.ecommerce.meterable.*;
import io.effects.samples.ecommerce.auditable.*;
import io.effects.samples.ecommerce.schedulable.*;

import java.time.Instant;

public class EcommerceApp {

    public static void main(String[] args) {
        System.out.println("========================================================================");
        System.out.println("🚀 STARTING B2B E-COMMERCE BULK CHECKOUT & SLA LIFECYCLE SIMULATION");
        System.out.println("========================================================================");

        // Run our happy-path simulation flow
        runHappyPathSimulation();

        System.out.println("\n========================================================================");
        System.out.println("🏁 SIMULATION COMPLETED SUCCESSFULLY!");
        System.out.println("========================================================================");
    }

    public static void runHappyPathSimulation() {
        Instant t0 = Instant.parse("2026-07-09T10:00:00Z");

        // 1. Initialize all 10 processes
        NegotiableProcess<String, BulkOrderTerms> negotiationProcess = new NegotiableProcess<>();
        ApprovalProcess<String, String, String> approvalProcess = new ApprovalProcess<>();
        PayableProcess<String, Double> paymentProcess = new PayableProcess<>();
        ReservationProcess<String, Integer> inventoryProcess = new ReservationProcess<>();
        FulfillmentProcess<String, Integer> shippingProcess = new FulfillmentProcess<>();
        OwnableProcess<String, String> ownershipProcess = new OwnableProcess<>();
        EntitleableProcess<String, WarrantyGrant, SLAContext> warrantyProcess = new EntitleableProcess<>();
        MeterableProcess<String, DiagnosticMetric, SLAInvoice> meterProcess = new MeterableProcess<>();
        AuditableProcess<String, AuditEntry, AuditCumulativeState> auditProcess = new AuditableProcess<>();
        SchedulableProcess<String, Instant> schedulerProcess = new SchedulableProcess<>();

        // IDs for our entities
        String orderId = "ORDER-B2B-5544";
        String itemId = "LAPTOP-DEV-PRO-2026";
        String customerEmail = "buyer-admin@enterprise-corp.com";
        String warehouseId = "REGION-WEST-WH-1";

        // Setup the target inventory pool capacity (100 units available)
        WarehouseInventoryPool warehousePool = new WarehouseInventoryPool(itemId, 100);

        System.out.println("[INFO] Initializing recipe registries...");
        inventoryProcess.register(itemId, warehousePool).unsafeRunSync();
        negotiationProcess.register(orderId, new BulkOrderNegotiation()).unsafeRunSync();
        paymentProcess.register(orderId, new OrderPayment()).unsafeRunSync();
        shippingProcess.register(orderId, new ShippingFulfillment()).unsafeRunSync();
        ownershipProcess.register(orderId, new AssetOwnership()).unsafeRunSync();
        warrantyProcess.register(customerEmail, new ExtendedWarrantyClearance()).unsafeRunSync();
        meterProcess.register(customerEmail, new PremiumSupportUsageMeter()).unsafeRunSync();
        auditProcess.register(orderId, new SecurityComplianceAuditLogger()).unsafeRunSync();
        schedulerProcess.register(orderId, new WarrantyPeriodicTask()).unsafeRunSync();
        System.out.println("[INFO] Recipe registries initialized successfully.");

        // --- 1. NEGOTIATE TERMS ---
        System.out.println("\n--- [STEP 1: NEGOTIATE TERMS] ---");
        BulkOrderTerms initialTerms = new BulkOrderTerms(50, 1500.0, 0.0);
        System.out.println("[NEGOTIATE] Buyer submits initial terms: " + initialTerms);
        negotiationProcess.initiate(orderId).unsafeRunSync();
        
        var offerRes =
            negotiationProcess.makeOffer(orderId, "buyer-admin", initialTerms, t0).unsafeRunSync();
        if (offerRes.isLeft()) {
            throw new RuntimeException("Offer failed: " + offerRes.getLeft());
        }

        System.out.println("[NEGOTIATE] Offer accepted in ledger. System counters with volume discount.");
        var counterTerms = new BulkOrderTerms(50, 1500.0, 40.0); // 40% discount
        var counterRes =
            negotiationProcess.makeCounter(orderId, "sales-system", counterTerms, t0.plusSeconds(60)).unsafeRunSync();
        if (counterRes.isLeft()) {
            throw new RuntimeException("Counter offer failed: " + counterRes.getLeft());
        }

        System.out.println("[NEGOTIATE] Counter terms updated. Buyer accepts discounted counter terms: 40%");
        var acceptRes =
            negotiationProcess.accept(orderId, "buyer-admin", t0.plusSeconds(120)).unsafeRunSync();
        if (acceptRes.isLeft()) {
            throw new RuntimeException("Negotiation acceptance failed: " + acceptRes.getLeft());
        }
        System.out.println("[SUCCESS] Bulk price negotiation completed and finalized!");

        // --- 2. MULTI-LEVEL APPROVAL FLOW ---
        System.out.println("\n--- [STEP 2: MULTI-LEVEL APPROVAL FLOW] ---");
        System.out.println("[APPROVAL] Discount of 40% exceeds standard auto-approval (15%). Submitting for VP & CFO approvals.");
        approvalProcess.register(orderId, new BulkOrderApproval(40.0)).unsafeRunSync();
        var submitRes =
            approvalProcess.submit(orderId, "sales-rep", "Bulk contract for 50 Laptops at 40% discount", t0.plusSeconds(130)).unsafeRunSync();
        if (submitRes.isLeft()) {
            throw new RuntimeException("Submit failed: " + submitRes.getLeft());
        }

        System.out.println("[APPROVAL] Submitted. Current status: " + submitRes.getRight().status() + ". Required: " + submitRes.getRight().requiredAuthority());
        var vpApproveRes =
            approvalProcess.approve(orderId, "vp-sarah", "SALES_VP", "Approved volume discount - strategic corporate account.", t0.plusSeconds(140)).unsafeRunSync();
        if (vpApproveRes.isLeft()) {
            throw new RuntimeException("Sales VP approval failed: " + vpApproveRes.getLeft());
        }

        System.out.println("[APPROVAL] Sales VP Approved. Status escalated. Next required: " + vpApproveRes.getRight().requiredAuthority());
        var cfoApproveRes =
            approvalProcess.approve(orderId, "cfo-steve", "CFO", "Financially approved.", t0.plusSeconds(150)).unsafeRunSync();
        if (cfoApproveRes.isLeft()) {
            throw new RuntimeException("CFO approval failed: " + cfoApproveRes.getLeft());
        }
        System.out.println("[APPROVAL] CFO Approved! Final status: " + cfoApproveRes.getRight().status());

        // --- 3. PAYMENT AUTHORIZATION ---
        System.out.println("\n--- [STEP 3: INITIAL PAYMENT AUTHORIZATION] ---");
        double totalPrice = 50 * 1500.0 * 0.60; // 50 Laptops at 40% discount = $45,000
        System.out.println("[PAYMENT] Authorizing purchase amount of $" + totalPrice);
        var authRes =
            paymentProcess.authorize(orderId, "buyer-admin", totalPrice, t0.plusSeconds(160)).unsafeRunSync();
        if (authRes.isLeft()) {
            throw new RuntimeException("Authorization failed: " + authRes.getLeft());
        }
        System.out.println("[PAYMENT] Credit authorized. Status: " + authRes.getRight().status());

        // --- 4. INVENTORY RESERVATION ---
        System.out.println("\n--- [STEP 4: WAREHOUSE INVENTORY RESERVATION] ---");
        System.out.println("[RESERVATION] Placing a hold on 50 laptops in warehouse: " + warehouseId);
        var holdRes =
            inventoryProcess.hold(itemId, "buyer-admin", 50, 3600, t0.plusSeconds(170)).unsafeRunSync();
        if (holdRes.isLeft()) {
            throw new RuntimeException("Hold failed: " + holdRes.getLeft());
        }
        Hold<String, Integer> hold = holdRes.getRight();
        System.out.println("[RESERVATION] Hold acquired. Hold ID: " + hold.holdId() + ". Expires at: " + hold.expiresAt());
        
        var resResult =
            inventoryProcess.confirm(hold.holdId(), t0.plusSeconds(200)).unsafeRunSync();
        if (resResult.isLeft()) {
            throw new RuntimeException("Confirmation failed: " + resResult.getLeft());
        }
        System.out.println("[RESERVATION] Reservation confirmed successfully! Reservation ID: " + resResult.getRight().reservationId());

        // --- 5. LOGISTICS FULFILLMENT ---
        System.out.println("\n--- [STEP 5: LOGISTICS SHIPPING FULFILLMENT] ---");
        System.out.println("[LOGISTICS] Initiating shipping fulfillment package tracking.");
        shippingProcess.initiate(orderId).unsafeRunSync();
        
        var allocRes =
            shippingProcess.allocate(orderId, "logistics-bot", 50, "Allocating 50 dev-pro laptops.", t0.plusSeconds(210)).unsafeRunSync();
        if (allocRes.isLeft()) {
            throw new RuntimeException("Allocation failed: " + allocRes.getLeft());
        }
        System.out.println("[LOGISTICS] Item quantities allocated. Current status: " + allocRes.getRight().status());

        var pkgRes =
            shippingProcess.packageItems(orderId, "logistics-bot", 50, "Boxed and labeled.", t0.plusSeconds(220)).unsafeRunSync();
        if (pkgRes.isLeft()) {
            throw new RuntimeException("Packaging failed: " + pkgRes.getLeft());
        }
        System.out.println("[LOGISTICS] Items boxed. Status: " + pkgRes.getRight().status());

        var dispatchRes =
            shippingProcess.dispatch(orderId, "carrier-fedex", "Dispatched via FedEx Express", t0.plusSeconds(230)).unsafeRunSync();
        if (dispatchRes.isLeft()) {
            throw new RuntimeException("Dispatch failed: " + dispatchRes.getLeft());
        }
        System.out.println("[LOGISTICS] Shipped in-transit! Status: " + dispatchRes.getRight().status());

        var completeRes =
            shippingProcess.complete(orderId, "buyer-admin", "Delivered and signed at corporate dock.", t0.plusSeconds(240)).unsafeRunSync();
        if (completeRes.isLeft()) {
            throw new RuntimeException("Completion failed: " + completeRes.getLeft());
        }
        System.out.println("[SUCCESS] Delivery completed!");

        // --- 6. ASSET OWNERSHIP REGISTRATION ---
        System.out.println("\n--- [STEP 6: ASSET OWNERSHIP REGISTRATION] ---");
        System.out.println("[OWNERSHIP] Assigning formal asset ownership of laptops to root administrator: " + customerEmail);
        var ownRes =
            ownershipProcess.assignOwner(orderId, customerEmail, t0.plusSeconds(250)).unsafeRunSync();
        if (ownRes.isLeft()) {
            throw new RuntimeException("Ownership assignment failed: " + ownRes.getLeft());
        }
        System.out.println("[OWNERSHIP] Owner registered successfully! Current owner: " + ownRes.getRight().currentOwner());

        // --- 7. WARRANTY ENTITLEMENTS ---
        System.out.println("\n--- [STEP 7: EXTENDED WARRANTY SLA CLEARANCE] ---");
        System.out.println("[WARRANTY] Owner grants PREMIUM SLA warranty clearance to device 'LAPTOP-DEVP-01'.");
        WarrantyGrant premiumGrant = new WarrantyGrant("LAPTOP-DEVP-01", "PREMIUM");
        warrantyProcess.initiate(customerEmail).unsafeRunSync();
        
        var grantRes =
            warrantyProcess.grant(customerEmail, "buyer-admin", premiumGrant, t0.plusSeconds(260)).unsafeRunSync();
        if (grantRes.isLeft()) {
            throw new RuntimeException("Grant failed: " + grantRes.getLeft());
        }

        System.out.println("[WARRANTY] Warranty grant completed. Testing SLA repair authorization context (Severity 5 repair request)...");
        SLAContext context = new SLAContext("REPAIR", 5);
        WarrantyGrant grantCheck = new WarrantyGrant("LAPTOP-DEVP-01", "PREMIUM");
        var checkRes =
            warrantyProcess.check(customerEmail, grantCheck, context, t0.plusSeconds(270)).unsafeRunSync();
        if (checkRes.isLeft()) {
            throw new RuntimeException("Check failed: " + checkRes.getLeft());
        }
        System.out.println("[WARRANTY] Severity 5 repair request authorized under Premium SLA! (Standard SLA would have failed).");

        // --- 8. SLA USAGE METERING ---
        System.out.println("\n--- [STEP 8: PREMIUM SUPPORT USAGE METERING] ---");
        System.out.println("[METER] Initiating support usage metrics for: " + customerEmail);
        meterProcess.initiate(customerEmail).unsafeRunSync();
        meterProcess.start(customerEmail, t0.plusSeconds(280)).unsafeRunSync();
        
        meterProcess.recordUsage(customerEmail, new DiagnosticMetric(3, 500), t0.plusSeconds(290)).unsafeRunSync(); // 3 calls, 500kb
        var usageRes =
            meterProcess.recordUsage(customerEmail, new DiagnosticMetric(4, 800), t0.plusSeconds(300)).unsafeRunSync(); // +4 calls, +800kb
        if (usageRes.isLeft()) {
            throw new RuntimeException("Record usage failed: " + usageRes.getLeft());
        }
        System.out.println("[METER] Support telemetry logged successfully.");

        // --- 9. SCHEDULER & BILLING SETTLEMENT ---
        System.out.println("\n--- [STEP 9: SCHEDULER TRIGGER & BILLING RUN] ---");
        Instant runTime = t0.plusSeconds(3600);
        System.out.println("[SCHEDULE] Scheduling billing settlement check to run at: " + runTime);
        var schedRes =
            schedulerProcess.schedule(orderId, "cron-system", runTime, t0.plusSeconds(310)).unsafeRunSync();
        if (schedRes.isLeft()) {
            throw new RuntimeException("Scheduling failed: " + schedRes.getLeft());
        }

        System.out.println("[SCHEDULE] Cron scheduled. Simulating scheduler firing at: " + runTime);
        var fireRes =
            schedulerProcess.fire(orderId, "cron-system", runTime).unsafeRunSync();
        if (fireRes.isLeft()) {
            throw new RuntimeException("Firing failed: " + fireRes.getLeft());
        }

        System.out.println("[SCHEDULE] Fired. Running rating cycle on meter ledger for support overages...");
        var rateRes = meterProcess.rate(customerEmail, runTime).unsafeRunSync();
        if (rateRes.isLeft()) {
            throw new RuntimeException("Rating failed: " + rateRes.getLeft());
        }
        SLAInvoice invoice = rateRes.getRight();
        System.out.println("[METER] Rated Invoice calculated: Total support overage fee is $" + invoice.totalOverageFee() + " " + invoice.currency());

        System.out.println("[PAYMENT] Capturing payment of $" + totalPrice + " against authorized order total...");
        var capRes =
            paymentProcess.capture(orderId, "buyer-admin", totalPrice, "Capture full amount upon warehouse dispatch.", runTime).unsafeRunSync();
        if (capRes.isLeft()) {
            throw new RuntimeException("Capture failed: " + capRes.getLeft());
        }
        System.out.println("[PAYMENT] Order capture finalized. Status: " + capRes.getRight().status() + ". Captured amount: $" + totalPrice);

        // --- 10. COMPLIANCE CRYPTO AUDITING ---
        System.out.println("\n--- [STEP 10: CRYPTOGRAPHIC COMPLIANCE AUDITING] ---");
        System.out.println("[AUDIT] Appending administrative order events into tamper-evident ledger...");
        auditProcess.initiate(orderId).unsafeRunSync();
        auditProcess.record(orderId, "buyer-admin", new AuditEntry("INITIATED", "buyer-admin", "SIGNATURE-MD5"), runTime).unsafeRunSync();
        auditProcess.record(orderId, "vp-sarah", new AuditEntry("DISCOUNT_APPROVED_VP", "vp-sarah", "SIGNATURE-SHA256"), runTime.plusSeconds(10)).unsafeRunSync();
        
        var auditStepRes =
            auditProcess.record(orderId, "cfo-steve", new AuditEntry("DISCOUNT_APPROVED_CFO", "cfo-steve", "SIGNATURE-SHA256-B"), runTime.plusSeconds(20)).unsafeRunSync();
        if (auditStepRes.isLeft()) {
            throw new RuntimeException("Audit recording failed: " + auditStepRes.getLeft());
        }

        System.out.println("[AUDIT] Administrative audit trailing completed. Replaying chronological ledger state...");
        var replayRes = auditProcess.replay(orderId).unsafeRunSync();
        if (replayRes.isLeft()) {
            throw new RuntimeException("Audit replay failed: " + replayRes.getLeft());
        }
        AuditCumulativeState state = replayRes.getRight();
        System.out.println("[AUDIT] Replayed security state compliance status: " + (state.isCompliant() ? "SECURE & COMPLIANT" : "NON-COMPLIANT"));
        System.out.println("[AUDIT] Recorded Chronology:");
        state.recordedActions().forEach(act -> System.out.println("  -> " + act));
    }
}
