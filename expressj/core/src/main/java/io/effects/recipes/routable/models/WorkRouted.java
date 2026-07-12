package io.effects.recipes.routable.models;

import io.effects.recipes.routable.RoutableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when work is successfully routed to a handler.
 */
public record WorkRouted<ID, H>(ID workId, H handlerId, Instant occurredAt) implements RoutableEvent<ID, H> {
    public WorkRouted {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(handlerId);
        Objects.requireNonNull(occurredAt);
    }
}
