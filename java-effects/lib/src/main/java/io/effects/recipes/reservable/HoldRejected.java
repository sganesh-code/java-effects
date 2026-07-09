package io.effects.recipes.reservable;

import java.time.Instant;

/**
 * Event published when a resource hold request is rejected.
 */
public record HoldRejected<ID, Q>(
    ID resourceId,
    String actorId,
    Q quantity,
    String reason,
    Instant occurredAt
) implements ReservationEvent<ID, Q> {}