package io.effects.recipes.negotiable.models;

import io.effects.recipes.negotiable.*;
import io.effects.recipes.negotiable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable step in the multi-party negotiation audit trail.
 */
public record NegotiationStep<P>(
    String stepId,
    String actorId,
    Type type,
    P proposal,
    Instant timestamp
) {
    public enum Type { OFFER, COUNTER, ACCEPT, WITHDRAW }

    public NegotiationStep {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(timestamp);
    }
}