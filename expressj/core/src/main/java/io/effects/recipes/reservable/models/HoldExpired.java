package io.effects.recipes.reservable.models;

import io.effects.recipes.reservable.*;
import io.effects.recipes.reservable.models.*;

import java.time.Instant;

/**
 * Event published when a resource hold expires.
 */
public record HoldExpired<ID, Q>(
    String holdId,
    ID resourceId,
    Instant occurredAt
) implements ReservationEvent<ID, Q> {}