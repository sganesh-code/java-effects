package io.effects.recipes.routable.models;

import io.effects.recipes.routable.RoutableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when work routing is explicitly rejected.
 */
public record RoutingRejected<ID, H>(ID workId, String reason, Instant occurredAt) implements RoutableEvent<ID, H> {
    public RoutingRejected {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(occurredAt);
    }
}
