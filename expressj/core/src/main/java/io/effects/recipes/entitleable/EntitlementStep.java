package io.effects.recipes.entitleable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable step in the entitlement audit trail.
 */
public record EntitlementStep<G>(
    String stepId,
    String actorId,
    Type type,
    G grant,
    Instant timestamp
) {
    public enum Type { GRANT, REVOKE }

    public EntitlementStep {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(grant);
        Objects.requireNonNull(timestamp);
    }
}