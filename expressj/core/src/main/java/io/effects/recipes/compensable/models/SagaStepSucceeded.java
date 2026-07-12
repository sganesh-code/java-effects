package io.effects.recipes.compensable.models;

import io.effects.recipes.compensable.CompensableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a specific step of a Saga completes successfully.
 */
public record SagaStepSucceeded<ID>(ID transactionId, String stepId, Instant occurredAt) implements CompensableEvent<ID> {
    public SagaStepSucceeded {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(occurredAt);
    }
}
