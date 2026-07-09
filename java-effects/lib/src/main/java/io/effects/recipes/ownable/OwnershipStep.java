package io.effects.recipes.ownable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable step in the ownership audit trail.
 */
public final class OwnershipStep {
    public enum Type { ASSIGN, TRANSFER, REVOKE }

    private final String stepId;
    private final String actorId;
    private final Type type;
    private final String comment;
    private final Instant timestamp;

    public OwnershipStep(String stepId, String actorId, Type type, String comment, Instant timestamp) {
        this.stepId = Objects.requireNonNull(stepId);
        this.actorId = Objects.requireNonNull(actorId);
        this.type = Objects.requireNonNull(type);
        this.comment = Objects.requireNonNull(comment);
        this.timestamp = Objects.requireNonNull(timestamp);
    }

    public String stepId() { return stepId; }
    public String actorId() { return actorId; }
    public Type type() { return type; }
    public String comment() { return comment; }
    public Instant timestamp() { return timestamp; }
}
