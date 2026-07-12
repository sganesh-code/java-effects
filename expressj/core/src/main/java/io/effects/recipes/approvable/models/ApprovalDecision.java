package io.effects.recipes.approvable.models;

import io.effects.recipes.approvable.*;
import io.effects.recipes.approvable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable decision step in the approval request's audit history.
 */
public record ApprovalDecision<A, C>(String stepId, String actorId, A actorRole, DecisionType type, C detail, Instant timestamp) {
    public ApprovalDecision(String stepId, String actorId, A actorRole, DecisionType type, C detail, Instant timestamp) {
        this.stepId = Objects.requireNonNull(stepId);
        this.actorId = Objects.requireNonNull(actorId);
        this.actorRole = actorRole; // Allow null for initiator
        this.type = Objects.requireNonNull(type);
        this.detail = Objects.requireNonNull(detail);
        this.timestamp = Objects.requireNonNull(timestamp);
    }
}