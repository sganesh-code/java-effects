package io.effects.recipes.approvable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable decision step in the approval request's audit history.
 */
public final class ApprovalDecision {
    private final String stepId;
    private final String actorId;
    private final String actorRole;
    private final DecisionType type;
    private final String comment;
    private final Instant timestamp;

    public ApprovalDecision(String stepId, String actorId, String actorRole, DecisionType type, String comment, Instant timestamp) {
        this.stepId = Objects.requireNonNull(stepId);
        this.actorId = Objects.requireNonNull(actorId);
        this.actorRole = Objects.requireNonNull(actorRole);
        this.type = Objects.requireNonNull(type);
        this.comment = Objects.requireNonNull(comment);
        this.timestamp = Objects.requireNonNull(timestamp);
    }

    public String stepId() { return stepId; }
    public String actorId() { return actorId; }
    public String actorRole() { return actorRole; }
    public DecisionType type() { return type; }
    public String comment() { return comment; }
    public Instant timestamp() { return timestamp; }
}
