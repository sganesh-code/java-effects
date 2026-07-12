package io.effects.recipes.routable.models;

import io.effects.recipes.routable.RoutableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when work is successfully rerouted to a new handler.
 */
public record WorkRerouted<ID, H>(ID workId, H previousHandlerId, H newHandlerId, Instant occurredAt) implements RoutableEvent<ID, H> {
    public WorkRerouted {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(previousHandlerId);
        Objects.requireNonNull(newHandlerId);
        Objects.requireNonNull(occurredAt);
    }
}
