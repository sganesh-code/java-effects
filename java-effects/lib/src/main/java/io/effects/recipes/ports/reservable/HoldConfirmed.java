package io.effects.recipes.ports.reservable;

import java.time.Instant;

/**
 * Event published when a resource hold is successfully confirmed.
 */
public record HoldConfirmed(
    String holdId,
    String reservationId,
    String resourceId,
    String actorId,
    int quantity,
    Instant occurredAt
) implements ReservationEvent {}
