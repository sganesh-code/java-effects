package io.effects.recipes.reservable;

import java.time.Instant;

/**
 * Event published when a resource hold is released.
 */
public record HoldReleased(
    String holdId,
    String resourceId,
    Instant occurredAt
) implements ReservationEvent {}
