package io.effects.recipes.schedulable.models;

import io.effects.recipes.schedulable.*;
import io.effects.recipes.schedulable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable step in the schedule audit trail.
 */
public final class ScheduleStep<T> {
    public enum Type { SCHEDULE, RESCHEDULE, FIRE, CANCEL }

    private final String stepId;
    private final String actorId;
    private final Type type;
    private final T detail;
    private final String comment;
    private final Instant timestamp;

    public ScheduleStep(String stepId, String actorId, Type type, T detail, String comment, Instant timestamp) {
        this.stepId = Objects.requireNonNull(stepId);
        this.actorId = Objects.requireNonNull(actorId);
        this.type = Objects.requireNonNull(type);
        this.detail = Objects.requireNonNull(detail);
        this.comment = Objects.requireNonNull(comment);
        this.timestamp = Objects.requireNonNull(timestamp);
    }

    public String stepId() { return stepId; }
    public String actorId() { return actorId; }
    public Type type() { return type; }
    public T detail() { return detail; }
    public String comment() { return comment; }
    public Instant timestamp() { return timestamp; }
}