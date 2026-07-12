package io.effects.recipes.reservable.models;

import io.effects.recipes.reservable.*;
import io.effects.recipes.reservable.models.*;

import java.time.Instant;

/**
 * An immutable register representing a confirmed reservation of a scarce resource.
 */
public record Reservation<ID, Q>(
    String reservationId,
    String holdId,
    String actorId,
    ID resourceId,
    Q quantity,
    Instant confirmedAt
) {}