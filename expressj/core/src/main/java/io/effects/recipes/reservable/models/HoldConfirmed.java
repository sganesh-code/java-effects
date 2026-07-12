package io.effects.recipes.reservable.models;

import io.effects.recipes.reservable.*;
import io.effects.recipes.reservable.models.*;

import java.time.Instant;

/**
 * Event published when a resource hold is successfully confirmed.
 */
public record HoldConfirmed<ID, Q>(
    String holdId,
    String reservationId,
    ID resourceId,
    String actorId,
    Q quantity,
    Instant occurredAt
) implements ReservationEvent<ID, Q> {}