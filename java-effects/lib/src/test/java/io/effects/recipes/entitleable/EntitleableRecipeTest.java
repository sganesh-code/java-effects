package io.effects.recipes.entitleable;

import io.effects.Either;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntitleableRecipeTest {

    // A custom, clean, non-anemic representation of an Entitlement Grant (Clearance)
    private record Clearance(String scope, Instant expiresAt) {
        public boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    // A custom, clean, non-anemic representation of a Check Context (AccessContext)
    private record AccessContext(String requestedScope) {}

    // A concrete, behavioral domain request representing a security rule block.
    private static final class SecurityAccessRules implements EntitleableRequest<String, Clearance, AccessContext> {

        SecurityAccessRules() {}

        @Override
        public Either<String, Void> evaluateGrant(EntitlementLedger<String, Clearance> ledger, Clearance grant, Instant now) {
            if (grant.isExpired(now)) {
                return Either.left("Cannot grant already expired clearance: " + grant.expiresAt());
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateCheck(EntitlementLedger<String, Clearance> ledger, Clearance grant, AccessContext context, Instant now) {
            // Replay outstanding active grants to evaluate access rule checks
            boolean hasValidGrant = ledger.activeGrants().stream()
                .anyMatch(step -> {
                    Clearance active = step.grant();
                    boolean scopeMatches = active.scope().equalsIgnoreCase(context.requestedScope());
                    boolean isValid = !active.isExpired(now);
                    return scopeMatches && isValid;
                });

            if (hasValidGrant) {
                return Either.right(null);
            }
            return Either.left("Access denied: Actor has no active valid grant for scope '" + context.requestedScope() + "'");
        }
    }

    // 1. Granting Entitlements & Temporal Expiry Laws
    @Test
    void testEntitlementGrantingAndExpiry() {
        EntitleableProcess<String, Clearance, AccessContext> process = new EntitleableProcess<>();
        SecurityAccessRules rules = new SecurityAccessRules();
        process.register("user-1", rules).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T14:00:00Z");

        process.initiate("user-1").unsafeRunSync();

        // Fails because grant is already expired
        Either<String, EntitlementStep<Clearance>> badGrant = process.grant("user-1", "admin-1", new Clearance("WRITE_ACCESS", t0.minusSeconds(10)), t0).unsafeRunSync();
        assertTrue(badGrant.isLeft());
        assertTrue(badGrant.getLeft().contains("Cannot grant already expired clearance"));

        // Step 1: Grant temporal "READ_ACCESS" (valid for 5 mins) -> succeeds
        Either<String, EntitlementStep<Clearance>> goodGrant = process.grant("user-1", "admin-1", new Clearance("READ_ACCESS", t0.plusSeconds(300)), t0).unsafeRunSync();
        assertTrue(goodGrant.isRight());
        EntitlementStep<Clearance> step = goodGrant.getRight();

        assertEquals(EntitlementStep.Type.GRANT, step.type());
        assertEquals("READ_ACCESS", step.grant().scope());

        // Evaluation A: Check at t0 + 10s -> Allowed
        Either<String, Void> allowedCheck = process.check("user-1", step.grant(), new AccessContext("READ_ACCESS"), t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(allowedCheck.isRight());

        // Evaluation B: Check at t0 + 301s -> Denied (Temporal Expiry Law!)
        Either<String, Void> deniedCheck = process.check("user-1", step.grant(), new AccessContext("READ_ACCESS"), t0.plusSeconds(301)).unsafeRunSync();
        assertTrue(deniedCheck.isLeft());
        assertTrue(deniedCheck.getLeft().contains("Access denied: Actor has no active valid grant"));
    }

    // 2. Grant Revocation Law
    @Test
    void testEntitlementRevocation() {
        InMemoryStateRepository<String, EntitlementLedger<String, Clearance>> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<EntitlementEvent<String>> publisher = new InMemoryEventPublisher<>();
        EntitleableProcess<String, Clearance, AccessContext> process = new EntitleableProcess<>(repository, publisher, new NoOpTelemetryPort());

        SecurityAccessRules rules = new SecurityAccessRules();
        process.register("user-2", rules).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T15:00:00Z");

        process.initiate("user-2").unsafeRunSync();

        Either<String, EntitlementStep<Clearance>> grantResult = process.grant("user-2", "admin-1", new Clearance("ADMIN_ACCESS", t0.plusSeconds(1000)), t0).unsafeRunSync();
        assertTrue(grantResult.isRight());
        EntitlementStep<Clearance> grantStep = grantResult.getRight();

        // Access check -> Allowed
        assertTrue(process.check("user-2", grantStep.grant(), new AccessContext("ADMIN_ACCESS"), t0.plusSeconds(10)).unsafeRunSync().isRight());

        // Revoke grant
        Either<String, EntitlementStep<Clearance>> revokeResult = process.revoke("user-2", "admin-1", grantStep.stepId(), t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(revokeResult.isRight());
        assertEquals(EntitlementStep.Type.REVOKE, revokeResult.getRight().type());

        // Access check after revocation -> Denied (Revocation Law!)
        Either<String, Void> deniedCheck = process.check("user-2", grantStep.grant(), new AccessContext("ADMIN_ACCESS"), t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(deniedCheck.isLeft());

        // Verify published events
        List<EntitlementEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(4, events.size()); // grant, check (allowed), revoke, check (denied)
        assertTrue(events.get(0) instanceof EntitlementEvent.EntitlementGranted);
        assertTrue(events.get(1) instanceof EntitlementEvent.EntitlementChecked);
        assertTrue(events.get(2) instanceof EntitlementEvent.EntitlementRevoked);
        assertTrue(events.get(3) instanceof EntitlementEvent.EntitlementChecked);
    }
}