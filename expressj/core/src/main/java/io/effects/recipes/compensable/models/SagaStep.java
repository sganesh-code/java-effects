package io.effects.recipes.compensable.models;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable record representing a step in the Saga execution history.
 */
public record SagaStep<C>(String stepId, Type type, C comment, Instant timestamp) {
    public enum Type { STEP_SUCCESS, ROLLBACK_TRIGGER, COMPENSATION_SUCCESS, COMPENSATION_FAILURE }

    public SagaStep {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(timestamp);
    }
}
