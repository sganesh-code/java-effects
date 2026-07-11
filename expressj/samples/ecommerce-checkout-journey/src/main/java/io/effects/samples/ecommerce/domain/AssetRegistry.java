package io.effects.samples.ecommerce.domain;

import io.effects.Either;
import io.effects.IO;
import io.effects.ports.EventSubscriber;
import io.effects.recipes.ownable.*;
import io.effects.recipes.entitleable.*;
import io.effects.samples.ecommerce.domain.models.*;

import java.time.Instant;

/**
 * First-class business object representing an Asset Registry.
 * It manages asset ownership registration and handles service level agreements (SLAs) / warranties
 * by directly implementing Ownable and Entitleable recipe processes.
 */
public class AssetRegistry implements
        OwnableRequest<String, String>,
        EntitleableRequest<String, WarrantyGrant, SLAContext> {

    private final OwnableProcess<String, String> ownershipProcess;
    private final EntitleableProcess<String, WarrantyGrant, SLAContext> warrantyProcess;
    private final EventSubscriber<Object> subscriberPort;
    private final String customerEmail;

    public AssetRegistry(EventSubscriber<Object> subscriberPort, String customerEmail) {
        this.ownershipProcess = new OwnableProcess<>();
        this.warrantyProcess = new EntitleableProcess<>();
        this.subscriberPort = subscriberPort;
        this.customerEmail = customerEmail;
        if (subscriberPort != null) {
            subscribeToEvents();
        }
    }

    public AssetRegistry() {
        this(null, null);
    }

    private void subscribeToEvents() {
        subscriberPort.subscribe("FulfillmentCompleted", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.fulfillable.FulfillmentCompleted<?, ?> event) {
                String orderId = event.fulfillmentId().toString();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[CHOREOGRAPHY] AssetRegistry caught FulfillmentCompleted event. Asynchronously registering ownership and granting premium warranty SLA for order: " + orderId);
                
                // Choreographed post-sale setup triggered automatically upon successful delivery completion
                registerOwner(orderId, customerEmail, now.plusSeconds(10));
                initiateWarranty(customerEmail);
                
                WarrantyGrant premiumGrant = new WarrantyGrant("LAPTOP-DEVP-01", "PREMIUM");
                grantWarrantySLA(customerEmail, "buyer-admin", premiumGrant, now.plusSeconds(20));
            }
            return null;
        })).unsafeRunSync();
    }

    // --- High-Level Business Behaviors ---

    public void registerOwner(String orderId, String ownerEmail, Instant time) {
        DomainLogger.info("[OWNERSHIP] Assigning formal asset ownership of laptops to root administrator: " + ownerEmail);
        ownershipProcess.register(orderId, this).unsafeRunSync();
        var res = ownershipProcess.assignOwner(orderId, ownerEmail, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Ownership assignment failed: " + res.getLeft());
        }
        DomainLogger.info("[OWNERSHIP] Owner registered successfully! Current owner: " + res.getRight().currentOwner());
    }

    public void initiateWarranty(String customerEmail) {
        warrantyProcess.register(customerEmail, this).unsafeRunSync();
        warrantyProcess.initiate(customerEmail).unsafeRunSync();
    }

    public void grantWarrantySLA(String customerEmail, String actorId, WarrantyGrant grant, Instant time) {
        DomainLogger.info("[WARRANTY] Owner grants " + grant.SLALevel() + " SLA warranty clearance to device '" + grant.deviceId() + "'.");
        var res = warrantyProcess.grant(customerEmail, actorId, grant, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Warranty grant failed: " + res.getLeft());
        }
        DomainLogger.info("[WARRANTY] Warranty grant completed.");
    }

    public void checkSLAAuthorization(String customerEmail, WarrantyGrant grant, SLAContext context, Instant time) {
        DomainLogger.info("Testing SLA repair authorization context (Severity " + context.severityLevel() + " " + context.requestType() + " request)...");
        var res = warrantyProcess.check(customerEmail, grant, context, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("SLA check failed: " + res.getLeft());
        }
        DomainLogger.info("[WARRANTY] Severity " + context.severityLevel() + " repair request authorized under " + grant.SLALevel() + " SLA!");
    }

    // --- OwnableRequest Interface Implementation ---

    @Override
    public Either<String, Void> evaluateInitialAssignment(String owner, Instant now) {
        if (owner == null || owner.isBlank()) {
            return Either.left("Initial owner must be specified.");
        }
        if (!owner.contains("@")) {
            return Either.left("Owner identifier must be a valid email address.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateTransfer(OwnershipRecord<String, String> record, String currentOwner, String proposedOwner, String actor, Instant now) {
        if (!currentOwner.equals(actor)) {
            return Either.left("Only the current owner (" + currentOwner + ") can initiate an ownership transfer.");
        }
        if (proposedOwner == null || proposedOwner.isBlank() || !proposedOwner.contains("@")) {
            return Either.left("Proposed owner must be a valid email address.");
        }
        return Either.right(null);
    }

    // --- EntitleableRequest Interface Implementation ---

    @Override
    public Either<String, Void> evaluateGrant(EntitlementLedger<String, WarrantyGrant> ledger, WarrantyGrant grant, Instant now) {
        if (grant.deviceId() == null || grant.deviceId().isBlank()) {
            return Either.left("Warranty grant must contain a valid device identifier.");
        }
        if (!"PREMIUM".equalsIgnoreCase(grant.SLALevel()) && !"STANDARD".equalsIgnoreCase(grant.SLALevel())) {
            return Either.left("Invalid SLA Level: must be PREMIUM or STANDARD.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateCheck(EntitlementLedger<String, WarrantyGrant> ledger, WarrantyGrant grant, SLAContext context, Instant now) {
        if ("REPAIR".equalsIgnoreCase(context.requestType()) && "STANDARD".equalsIgnoreCase(grant.SLALevel()) && context.severityLevel() > 3) {
            return Either.left("Standard Warranty does not cover high-severity repairs (Severity " + context.severityLevel() + "). Upgrade required.");
        }

        return Either.right(null);
    }
}
