package io.effects.recipes.reservable;

import java.time.Instant;

/**
 * An immutable record representing a confirmed reservation of a scarce resource.
 */
public record Reservation(
    String reservationId,
    String holdId,
    String actorId,
    String resourceId,
    int quantity,
    Instant confirmedAt
) {}
