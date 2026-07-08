package io.effects.recipes.reservable;

import java.time.Instant;

/**
 * Event published when a resource hold expires.
 */
public record HoldExpired(
    String holdId,
    String resourceId,
    Instant occurredAt
) implements ReservationEvent {}
