package io.effects.recipes.throttlable.models;

import io.effects.recipes.throttlable.ThrottlableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a bucket is refilled with tokens.
 */
public record QuotaRefilled<ID>(ID actorId, double refilledTokens, double currentCapacity, Instant occurredAt) implements ThrottlableEvent<ID> {
    public QuotaRefilled {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(occurredAt);
    }
}
