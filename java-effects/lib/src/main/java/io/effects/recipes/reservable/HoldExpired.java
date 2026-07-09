package io.effects.recipes.reservable;

import java.time.Instant;

/**
 * Event published when a resource hold expires.
 */
public record HoldExpired<ID, Q>(
    String holdId,
    ID resourceId,
    Instant occurredAt
) implements ReservationEvent<ID, Q> {}