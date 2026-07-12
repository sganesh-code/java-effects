package io.effects.recipes.claimable.models;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable step in the claim audit trail.
 */
public record ClaimStep<C>(String stepId, String actorId, Type type, C comment, Instant timestamp) {
    public enum Type { FILE, REVIEW, DISPUTE, ACCEPT, DENY }

    public ClaimStep {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(timestamp);
    }
}
