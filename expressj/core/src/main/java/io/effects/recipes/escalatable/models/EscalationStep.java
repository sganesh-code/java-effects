package io.effects.recipes.escalatable.models;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable record representing a step in the escalation audit trail.
 */
public record EscalationStep<T, C>(String stepId, T tier, String handlerId, Type type, C comment, Instant timestamp) {
    public enum Type { INITIALIZE, WARNING, ESCALATE, DEESCALATE, REASSIGN }

    public EscalationStep {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(timestamp);
    }
}
