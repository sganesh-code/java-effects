package io.effects.recipes.reservable;

import java.time.Instant;

/**
 * Event published when a resource hold is successfully confirmed.
 */
record HoldConfirmed(
    String holdId,
    String reservationId,
    String resourceId,
    String actorId,
    int quantity,
    Instant occurredAt
) implements ReservationEvent {}
