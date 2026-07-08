package io.effects.recipes.ports.reservable;

import java.time.Instant;

/**
 * Event published when a resource hold request is rejected.
 */
public record HoldRejected(
    String resourceId,
    String actorId,
    int quantity,
    String reason,
    Instant occurredAt
) implements ReservationEvent {}
