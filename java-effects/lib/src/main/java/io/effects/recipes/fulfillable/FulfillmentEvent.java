package io.effects.recipes.fulfillable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the fulfillment recipe.
 */
public interface FulfillmentEvent {

    /**
     * Unique identifier of the fulfillment.
     */
    String fulfillmentId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
