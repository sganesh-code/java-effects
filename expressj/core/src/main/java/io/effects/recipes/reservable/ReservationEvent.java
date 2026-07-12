package io.effects.recipes.reservable;

import io.effects.recipes.reservable.models.*;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the reservation recipe.
 */
public interface ReservationEvent<ID, Q> {

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();

    /**
     * The unique identifier of the target scarce resource.
     */
    ID resourceId();
}