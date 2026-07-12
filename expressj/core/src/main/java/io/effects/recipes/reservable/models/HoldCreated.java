package io.effects.recipes.reservable.models;

import io.effects.recipes.reservable.*;
import io.effects.recipes.reservable.models.*;

import java.time.Instant;

/**
 * Event published when a resource hold is successfully created.
 */
public record HoldCreated<ID, Q>(
    String holdId,
    ID resourceId,
    String actorId,
    Q quantity,
    Instant expiresAt,
    Instant occurredAt
) implements ReservationEvent<ID, Q> {}