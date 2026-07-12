package io.effects.recipes.claimable.models;

import io.effects.recipes.claimable.ClaimableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a claim is denied with a reason.
 */
public record ClaimDenied<ID>(ID claimId, String reviewerId, String reason, Instant occurredAt) implements ClaimableEvent<ID> {
    public ClaimDenied {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(reviewerId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(occurredAt);
    }
}
