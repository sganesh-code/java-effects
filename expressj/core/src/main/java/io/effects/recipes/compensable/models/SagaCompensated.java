package io.effects.recipes.compensable.models;

import io.effects.recipes.compensable.CompensableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when all completed steps have been successfully compensated.
 */
public record SagaCompensated<ID>(ID transactionId, Instant occurredAt) implements CompensableEvent<ID> {
    public SagaCompensated {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(occurredAt);
    }
}
