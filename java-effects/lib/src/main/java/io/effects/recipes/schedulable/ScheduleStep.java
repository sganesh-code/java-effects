package io.effects.recipes.schedulable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable step in the schedule audit trail.
 */
public final class ScheduleStep {
    public enum Type { SCHEDULE, RESCHEDULE, FIRE, CANCEL }

    private final String stepId;
    private final String actorId;
    private final Type type;
    private final Instant targetTime;
    private final String comment;
    private final Instant timestamp;

    public ScheduleStep(String stepId, String actorId, Type type, Instant targetTime, String comment, Instant timestamp) {
        this.stepId = Objects.requireNonNull(stepId);
        this.actorId = Objects.requireNonNull(actorId);
        this.type = Objects.requireNonNull(type);
        this.targetTime = targetTime;
        this.comment = Objects.requireNonNull(comment);
        this.timestamp = Objects.requireNonNull(timestamp);
    }

    public String stepId() { return stepId; }
    public String actorId() { return actorId; }
    public Type type() { return type; }
    public Instant targetTime() { return targetTime; }
    public String comment() { return comment; }
    public Instant timestamp() { return timestamp; }
}
