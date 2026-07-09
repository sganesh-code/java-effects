package io.effects.recipes.reservable;

import java.time.Instant;

/**
 * An immutable record representing a confirmed reservation of a scarce resource.
 */
public record Reservation<ID, Q>(
    String reservationId,
    String holdId,
    String actorId,
    ID resourceId,
    Q quantity,
    Instant confirmedAt
) {}