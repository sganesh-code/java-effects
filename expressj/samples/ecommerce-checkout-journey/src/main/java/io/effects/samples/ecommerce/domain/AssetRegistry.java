package io.effects.samples.ecommerce.domain;

import io.effects.Either;
import io.effects.IO;
import io.effects.ports.EventSubscriber;
import io.effects.recipes.ownable.*;
import io.effects.recipes.entitleable.*;
import io.effects.samples.ecommerce.domain.models.*;
import java.time.Instant;

/**
 * Represents a corporate asset and warranty registry. 
 * It manages hardware device ownership assignment (such as linking laptop assets to employee accounts)
 * and handles corporate Service Level Agreement (SLA) technical support clearances.
 */
public class AssetRegistry implements
        OwnableRequest<String, String>,
        EntitleableRequest<String, WarrantyGrant, SLAContext> {

    private final OwnableProcess<String, String> ownershipProcess;
    private final EntitleableProcess<String, WarrantyGrant, SLAContext> warrantyProcess;
    private final EventSubscriber<Object> subscriberPort;
    private final String customerEmail;

    /**
     * Initializes an asset registry database mapped to a specific customer email.
     */
    public AssetRegistry(EventSubscriber<Object> subscriberPort, String customerEmail) {
        this.ownershipProcess = new OwnableProcess<>();
        this.warrantyProcess = new EntitleableProcess<>();
        this.subscriberPort = subscriberPort;
        this.customerEmail = customerEmail;
        if (subscriberPort != null) {
            setupAssetTriggers();
        }
    }

    public AssetRegistry() {
        this(null, null);
    }

    /**
     * Configures automatic triggers to assign hardware ownership and activate executive SLAs 
     * immediately once shipping courier reports delivery completion.
     */
    private void setupAssetTriggers() {
        subscriberPort.subscribe("FulfillmentCompleted", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.fulfillable.FulfillmentCompleted<?, ?> event) {
                String orderId = event.fulfillmentId().toString();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[WARRANTY] Delivery confirmed by courier. Automatically registering hardware ownership and activating premium SLA support for order: " + orderId);
                
                registerOwner(orderId, customerEmail, now.plusSeconds(10));
                initiateWarranty(customerEmail);
                
                WarrantyGrant premiumGrant = new WarrantyGrant("LAPTOP-DEVP-01", "PREMIUM");
                grantWarrantySLA(customerEmail, "buyer-admin", premiumGrant, now.plusSeconds(20));
            }
            return null;
        })).unsafeRunSync();
    }

    // --- Core Asset & SLA Operations ---

    /**
     * Registers the formal corporate owner/employee email address associated with a delivered physical asset.
     */
    public void registerOwner(String orderId, String ownerEmail, Instant time) {
        DomainLogger.info("[OWNERSHIP] Registering formal asset ownership of corporate laptops to: " + ownerEmail);
        ownershipProcess.register(orderId, this).unsafeRunSync();
        var res = ownershipProcess.assignOwner(orderId, ownerEmail, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Ownership assignment failed: " + res.getLeft());
        }
        DomainLogger.info("[OWNERSHIP] Asset ownership logged successfully! Current registered owner: " + res.getRight().currentOwner());
    }

    /**
     * Activates a service contract warranty profile for a customer corporate account.
     */
    public void initiateWarranty(String customerEmail) {
        warrantyProcess.register(customerEmail, this).unsafeRunSync();
        warrantyProcess.initiate(customerEmail).unsafeRunSync();
    }

    /**
     * Grants a specific Service Level Agreement (SLA) support level (e.g. PREMIUM) to a physical device.
     */
    public void grantWarrantySLA(String customerEmail, String actorId, WarrantyGrant grant, Instant time) {
        DomainLogger.info("[WARRANTY] Granting " + grant.SLALevel() + " support SLA coverage to device serial: " + grant.deviceId());
        var res = warrantyProcess.grant(customerEmail, actorId, grant, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("SLA grant failed: " + res.getLeft());
        }
        DomainLogger.info("[WARRANTY] Support SLA registered.");
    }

    /**
     * Audits and verifies if a repair or replacement ticket is authorized under the active SLA terms.
     */
    public void checkSLAAuthorization(String customerEmail, WarrantyGrant grant, SLAContext context, Instant time) {
        DomainLogger.info("[WARRANTY] Verifying support ticket authorization (SLA Level: " + grant.SLALevel() + ")...");
        var res = warrantyProcess.check(customerEmail, grant, context, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("SLA authorization check failed: " + res.getLeft());
        }
        DomainLogger.info("[WARRANTY] Severity " + context.severityLevel() + " support request authorized under " + grant.SLALevel() + " SLA agreement!");
    }

    // --- Internal Business Invariants & Policies ---

    @Override
    public Either<String, Void> evaluateInitialAssignment(String owner, Instant now) {
        if (owner == null || owner.isBlank()) {
            return Either.left("Initial asset owner must be explicitly defined.");
        }
        if (!owner.contains("@")) {
            return Either.left("Owner identifier must be a valid email address.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateTransfer(OwnershipRecord<String, String> record, String currentOwner, String proposedOwner, String actor, Instant now) {
        if (!currentOwner.equals(actor)) {
            return Either.left("Only the registered owner (" + currentOwner + ") is authorized to initiate asset ownership transfers.");
        }
        if (proposedOwner == null || proposedOwner.isBlank() || !proposedOwner.contains("@")) {
            return Either.left("Proposed asset transferee must be a valid email address.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateGrant(EntitlementLedger<String, WarrantyGrant> ledger, WarrantyGrant grant, Instant now) {
        if (grant.deviceId() == null || grant.deviceId().isBlank()) {
            return Either.left("Warranty SLA grant must be assigned to a valid physical device serial.");
        }
        if (!"PREMIUM".equalsIgnoreCase(grant.SLALevel()) && !"STANDARD".equalsIgnoreCase(grant.SLALevel())) {
            return Either.left("Invalid SLA service tier level: must be PREMIUM or STANDARD.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateCheck(EntitlementLedger<String, WarrantyGrant> ledger, WarrantyGrant grant, SLAContext context, Instant now) {
        if ("REPAIR".equalsIgnoreCase(context.requestType()) && "STANDARD".equalsIgnoreCase(grant.SLALevel()) && context.severityLevel() > 3) {
            return Either.left("Standard SLA does not cover high-severity hardware repairs (Severity " + context.severityLevel() + "). Upgrade required.");
        }

        return Either.right(null);
    }
}
