package io.effects.recipes.claimable.models;

import io.effects.recipes.claimable.ClaimableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a claim is initially filed.
 */
public record ClaimFiled<ID>(ID claimId, String claimantId, Instant occurredAt) implements ClaimableEvent<ID> {
    public ClaimFiled {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(claimantId);
        Objects.requireNonNull(occurredAt);
    }
}
