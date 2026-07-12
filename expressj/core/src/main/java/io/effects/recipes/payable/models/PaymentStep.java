package io.effects.recipes.payable.models;

import io.effects.recipes.payable.*;
import io.effects.recipes.payable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable transaction step in the payment audit trail.
 */
public record PaymentStep<M>(String stepId, String actorId, Type type, M detail, String comment, Instant timestamp) {
    public enum Type {AUTHORIZE, CAPTURE, REVERSE, REFUND}

    public PaymentStep(String stepId, String actorId, Type type, M detail, String comment, Instant timestamp) {
        this.stepId = Objects.requireNonNull(stepId);
        this.actorId = Objects.requireNonNull(actorId);
        this.type = Objects.requireNonNull(type);
        this.detail = Objects.requireNonNull(detail);
        this.comment = Objects.requireNonNull(comment);
        this.timestamp = Objects.requireNonNull(timestamp);
    }
}
