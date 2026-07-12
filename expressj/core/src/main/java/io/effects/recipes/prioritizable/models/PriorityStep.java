package io.effects.recipes.prioritizable.models;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable step in the priority audit trail.
 */
public record PriorityStep<P, C>(String stepId, P priority, Type type, C comment, Instant timestamp) {
    public enum Type { SEQUENCE, REPRIORITIZE, DEFER, EXPEDITE }

    public PriorityStep {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(timestamp);
    }
}
