package io.effects.recipes.fulfillable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable step in the fulfillment audit trail.
 */
public final class FulfillmentStep {
    public enum Type { ALLOCATE, PACKAGE, DISPATCH, COMPLETE, RELEASE }

    private final String stepId;
    private final String actorId;
    private final Type type;
    private final int quantity;
    private final String comment;
    private final Instant timestamp;

    public FulfillmentStep(String stepId, String actorId, Type type, int quantity, String comment, Instant timestamp) {
        this.stepId = Objects.requireNonNull(stepId);
        this.actorId = Objects.requireNonNull(actorId);
        this.type = Objects.requireNonNull(type);
        if (quantity < 0) {
            throw new IllegalArgumentException("Fulfillment step quantity cannot be negative");
        }
        this.quantity = quantity;
        this.comment = Objects.requireNonNull(comment);
        this.timestamp = Objects.requireNonNull(timestamp);
    }

    public String stepId() { return stepId; }
    public String actorId() { return actorId; }
    public Type type() { return type; }
    public int quantity() { return quantity; }
    public String comment() { return comment; }
    public Instant timestamp() { return timestamp; }
}
