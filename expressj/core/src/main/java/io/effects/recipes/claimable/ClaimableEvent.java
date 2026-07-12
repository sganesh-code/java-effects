package io.effects.recipes.claimable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the claimable recipe.
 */
public interface ClaimableEvent<ID> {

    /**
     * Unique identifier of the claim.
     */
    ID claimId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
