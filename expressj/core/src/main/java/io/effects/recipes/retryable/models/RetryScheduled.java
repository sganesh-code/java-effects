package io.effects.recipes.retryable.models;

import io.effects.recipes.retryable.RetryableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a retry is scheduled with a backoff delay.
 */
public record RetryScheduled<ID>(ID operationId, int nextAttempt, long delayMillis, Instant occurredAt) implements RetryableEvent<ID> {
    public RetryScheduled {
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(occurredAt);
    }
}
