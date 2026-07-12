package io.effects.recipes.routable.models;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable step in the routing audit trail.
 */
public record RoutingStep<H, C>(String stepId, H handler, Type type, C comment, Instant timestamp) {
    public enum Type { ROUTE, REROUTE, REJECT }

    public RoutingStep {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(timestamp);
    }
}
