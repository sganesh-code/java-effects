package io.effects.recipes.payable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable transaction step in the payment audit trail.
 */
public final class PaymentStep {
    public enum Type { AUTHORIZE, CAPTURE, REVERSE, REFUND }

    private final String stepId;
    private final String actorId;
    private final Type type;
    private final double amount;
    private final String comment;
    private final Instant timestamp;

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

    public String stepId() { return stepId; }
    public String actorId() { return actorId; }
    public Type type() { return type; }
    public double amount() { return amount; }
    public String comment() { return comment; }
    public Instant timestamp() { return timestamp; }
}
