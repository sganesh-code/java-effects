package io.effects.recipes.routable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the routable recipe.
 */
public interface RoutableEvent<ID, H> {

    /**
     * Unique identifier of the routed work.
     */
    ID workId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
