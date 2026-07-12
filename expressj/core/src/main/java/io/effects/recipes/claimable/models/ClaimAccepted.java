package io.effects.recipes.claimable.models;

import io.effects.recipes.claimable.ClaimableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a claim is accepted.
 */
public record ClaimAccepted<ID>(ID claimId, String reviewerId, Instant occurredAt) implements ClaimableEvent<ID> {
    public ClaimAccepted {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(reviewerId);
        Objects.requireNonNull(occurredAt);
    }
}
