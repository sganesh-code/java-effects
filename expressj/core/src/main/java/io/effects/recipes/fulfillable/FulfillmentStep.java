package io.effects.recipes.fulfillable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable step in the fulfillment audit trail.
 */
public final class FulfillmentStep<Q> {
    public enum Type { ALLOCATE, PACKAGE, DISPATCH, COMPLETE, RELEASE }

    private final String stepId;
    private final String actorId;
    private final Type type;
    private final Q detail;
    private final String comment;
    private final Instant timestamp;

    public FulfillmentStep(String stepId, String actorId, Type type, Q detail, String comment, Instant timestamp) {
        this.stepId = Objects.requireNonNull(stepId);
        this.actorId = Objects.requireNonNull(actorId);
        this.type = Objects.requireNonNull(type);
        this.detail = Objects.requireNonNull(detail);
        this.comment = Objects.requireNonNull(comment);
        this.timestamp = Objects.requireNonNull(timestamp);
    }

    public String stepId() { return stepId; }
    public String actorId() { return actorId; }
    public Type type() { return type; }
    public Q detail() { return detail; }
    public String comment() { return comment; }
    public Instant timestamp() { return timestamp; }
}