package io.effects.recipes.retryable.models;

import io.effects.recipes.retryable.RetryableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a retryable operation executes successfully.
 */
public record ExecutionSucceeded<ID>(ID operationId, int attempts, Instant occurredAt) implements RetryableEvent<ID> {
    public ExecutionSucceeded {
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(occurredAt);
    }
}
