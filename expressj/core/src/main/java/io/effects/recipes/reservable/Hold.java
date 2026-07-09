package io.effects.recipes.reservable;

import java.time.Instant;

/**
 * An immutable record representing a temporary claim on a scarce resource.
 */
public record Hold<ID, Q>(
    String holdId,
    String actorId,
    ID resourceId,
    Q quantity,
    Instant expiresAt,
    Status status
) {
    public enum Status { HELD, CONFIRMED, RELEASED, EXPIRED }

    /**
     * Transitions this Hold into a new status.
     */
    public Hold<ID, Q> withStatus(Status newStatus) {
        return new Hold<>(holdId, actorId, resourceId, quantity, expiresAt, newStatus);
    }
}