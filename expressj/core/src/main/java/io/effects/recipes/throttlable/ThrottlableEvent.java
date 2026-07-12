package io.effects.recipes.throttlable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the throttlable recipe.
 */
public interface ThrottlableEvent<ID> {

    /**
     * Unique identifier of the throttled actor.
     */
    ID actorId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
