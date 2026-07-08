package io.effects.recipes.approvable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable decision step in the approval request's audit history.
 */
public record ApprovalDecision(String stepId, String actorId, String actorRole, DecisionType type, String comment,
                               Instant timestamp) {
    public ApprovalDecision(String stepId, String actorId, String actorRole, DecisionType type, String comment, Instant timestamp) {
        this.stepId = Objects.requireNonNull(stepId);
        this.actorId = Objects.requireNonNull(actorId);
        this.actorRole = Objects.requireNonNull(actorRole);
        this.type = Objects.requireNonNull(type);
        this.comment = Objects.requireNonNull(comment);
        this.timestamp = Objects.requireNonNull(timestamp);
    }
}
