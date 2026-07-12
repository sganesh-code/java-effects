package io.effects.recipes.prioritizable.models;

import io.effects.recipes.prioritizable.PrioritizableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an item has been deferred.
 */
public record WorkDeferred<ID, P>(ID workId, Instant deferredUntil, Instant occurredAt) implements PrioritizableEvent<ID, P> {
    public WorkDeferred {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(deferredUntil);
        Objects.requireNonNull(occurredAt);
    }
}
