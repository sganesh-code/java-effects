package io.effects.recipes.ports.reservable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the reservation recipe.
 */
public interface ReservationEvent {

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();

    /**
     * The unique identifier of the target scarce resource.
     */
    String resourceId();
}
