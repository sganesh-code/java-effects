package io.effects.recipes.compensable.models;

import io.effects.recipes.compensable.CompensableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a failure is detected in a Saga, triggering compensating rollbacks.
 */
public record SagaRollbackTriggered<ID>(ID transactionId, String failedStepId, String reason, Instant occurredAt) implements CompensableEvent<ID> {
    public SagaRollbackTriggered {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(failedStepId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(occurredAt);
    }
}
