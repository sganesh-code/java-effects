package io.effects.recipes.claimable.models;

import io.effects.recipes.claimable.ClaimableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a claim moves to active review/evaluation.
 */
public record ClaimUnderReview<ID>(ID claimId, String reviewerId, Instant occurredAt) implements ClaimableEvent<ID> {
    public ClaimUnderReview {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(reviewerId);
        Objects.requireNonNull(occurredAt);
    }
}
