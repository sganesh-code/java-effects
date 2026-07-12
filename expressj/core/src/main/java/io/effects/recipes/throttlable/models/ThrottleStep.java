package io.effects.recipes.throttlable.models;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable record representing a step in the throttling audit trail.
 */
public record ThrottleStep<C>(String stepId, double tokens, Type type, C comment, Instant timestamp) {
    public enum Type { CONSUME_SUCCESS, THROTTLE_REJECT, REFILL }

    public ThrottleStep {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(timestamp);
    }
}
