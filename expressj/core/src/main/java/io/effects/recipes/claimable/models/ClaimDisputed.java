package io.effects.recipes.claimable.models;

import io.effects.recipes.claimable.ClaimableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a claimant disputes a denial or evaluation.
 */
public record ClaimDisputed<ID>(ID claimId, String actorId, String reason, Instant occurredAt) implements ClaimableEvent<ID> {
    public ClaimDisputed {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(occurredAt);
    }
}
