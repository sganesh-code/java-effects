package io.effects.recipes.retryable.models;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable step in the retry execution audit trail.
 */
public record RetryStep<C>(String stepId, int attempt, Type type, C comment, Instant timestamp) {
    public enum Type { EXECUTE_ATTEMPT, ATTEMPT_FAILURE, SCHEDULE_RETRY, SUCCESS, ABANDON }

    public RetryStep {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(timestamp);
    }
}
