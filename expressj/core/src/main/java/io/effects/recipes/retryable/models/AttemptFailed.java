package io.effects.recipes.retryable.models;

import io.effects.recipes.retryable.RetryableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an execution attempt fails, before retry or abandonment.
 */
public record AttemptFailed<ID>(ID operationId, int attempt, String errorMessage, Instant occurredAt) implements RetryableEvent<ID> {
    public AttemptFailed {
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(errorMessage);
        Objects.requireNonNull(occurredAt);
    }
}
