package io.effects.recipes.retryable.models;

import io.effects.recipes.retryable.RetryableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an operation permanently fails and all retries are exhausted.
 */
public record ExecutionAbandoned<ID>(ID operationId, int totalAttempts, String finalErrorMessage, Instant occurredAt) implements RetryableEvent<ID> {
    public ExecutionAbandoned {
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(finalErrorMessage);
        Objects.requireNonNull(occurredAt);
    }
}
