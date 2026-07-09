package io.effects.recipes.payable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable transaction step in the payment audit trail.
 */
public record PaymentStep(String stepId, String actorId, Type type, double amount, String comment, Instant timestamp) {
    public enum Type {AUTHORIZE, CAPTURE, REVERSE, REFUND}

    public PaymentStep(String stepId, String actorId, Type type, double amount, String comment, Instant timestamp) {
        this.stepId = Objects.requireNonNull(stepId);
        this.actorId = Objects.requireNonNull(actorId);
        this.type = Objects.requireNonNull(type);
        if (amount < 0.0) {
            throw new IllegalArgumentException("Transaction step amount cannot be negative");
        }
        this.amount = amount;
        this.comment = Objects.requireNonNull(comment);
        this.timestamp = Objects.requireNonNull(timestamp);
    }
}
