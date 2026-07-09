package io.effects.recipes.ownable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the ownership recipe.
 */
public interface OwnershipEvent {

    /**
     * Unique identifier of the owned asset.
     */
    String assetId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
