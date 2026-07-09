package io.effects.recipes.ownable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable step in the ownership audit trail.
 */
public record OwnershipStep(String stepId, String actorId, Type type, String comment, Instant timestamp) {
    public enum Type {ASSIGN, TRANSFER, REVOKE}

    public OwnershipStep(String stepId, String actorId, Type type, String comment, Instant timestamp) {
        this.stepId = Objects.requireNonNull(stepId);
        this.actorId = Objects.requireNonNull(actorId);
        this.type = Objects.requireNonNull(type);
        this.comment = Objects.requireNonNull(comment);
        this.timestamp = Objects.requireNonNull(timestamp);
    }
}
