package io.effects.recipes.throttlable.models;

import io.effects.recipes.throttlable.ThrottlableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when token consumption is rejected/throttled.
 */
public record RateThrottled<ID>(ID actorId, double requestedTokens, double availableTokens, Instant occurredAt) implements ThrottlableEvent<ID> {
    public RateThrottled {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(occurredAt);
    }
}
