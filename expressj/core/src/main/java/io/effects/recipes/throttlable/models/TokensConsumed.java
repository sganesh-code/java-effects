package io.effects.recipes.throttlable.models;

import io.effects.recipes.throttlable.ThrottlableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when tokens are successfully consumed from the bucket.
 */
public record TokensConsumed<ID>(ID actorId, double tokensConsumed, double remainingCapacity, Instant occurredAt) implements ThrottlableEvent<ID> {
    public TokensConsumed {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(occurredAt);
    }
}
