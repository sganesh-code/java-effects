package io.effects.recipes.entitleable;

import io.effects.recipes.entitleable.models.*;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the entitleable recipe.
 */
public interface EntitlementEvent<ID> {

    /**
     * Unique identifier of the actor or resource possessing the entitlements.
     */
    ID actorId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();

    /**
     * Event published when a new entitlement is granted.
     */
    record EntitlementGranted<ID, G>(ID actorId, String stepId, G grant, Instant occurredAt) implements EntitlementEvent<ID> {}

    /**
     * Event published when an active entitlement is successfully revoked.
     */
    record EntitlementRevoked<ID, G>(ID actorId, String stepId, G grant, Instant occurredAt) implements EntitlementEvent<ID> {}

    /**
     * Event published when an entitlement capability check is evaluated.
     */
    record EntitlementChecked<ID, G, C>(ID actorId, G grant, C context, boolean allowed, Instant occurredAt) implements EntitlementEvent<ID> {}
}