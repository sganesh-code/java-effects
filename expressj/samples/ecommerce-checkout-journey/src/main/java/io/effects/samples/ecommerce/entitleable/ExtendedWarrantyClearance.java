package io.effects.samples.ecommerce.entitleable;

import io.effects.Either;
import io.effects.recipes.entitleable.EntitleableRequest;
import io.effects.recipes.entitleable.EntitlementLedger;
import java.time.Instant;

public class ExtendedWarrantyClearance implements EntitleableRequest<String, WarrantyGrant, SLAContext> {

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
        if ("REPAIR".equalsIgnoreCase(context.requestType()) && "STANDARD".equalsIgnoreCase(grant.SLALevel())) {
            if (context.severityLevel() > 3) {
                return Either.left("Standard Warranty does not cover high-severity repairs (Severity " + context.severityLevel() + "). Upgrade required.");
            }
        }
        return Either.right(null);
    }
}
