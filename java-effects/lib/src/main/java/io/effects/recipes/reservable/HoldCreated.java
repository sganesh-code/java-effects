package io.effects.recipes.reservable;

import java.time.Instant;

/**
 * Event published when a resource hold is successfully created.
 */
public record HoldCreated(
    String holdId,
    String resourceId,
    String actorId,
    int quantity,
    Instant expiresAt,
    Instant occurredAt
) implements ReservationEvent {}
