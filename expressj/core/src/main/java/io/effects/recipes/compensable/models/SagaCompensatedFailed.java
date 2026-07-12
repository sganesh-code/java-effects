package io.effects.recipes.compensable.models;

import io.effects.recipes.compensable.CompensableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a compensating step fails to execute, indicating inconsistency.
 */
public record SagaCompensatedFailed<ID>(ID transactionId, String stepId, String error, Instant occurredAt) implements CompensableEvent<ID> {
    public SagaCompensatedFailed {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(error);
        Objects.requireNonNull(occurredAt);
    }
}
