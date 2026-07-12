package io.effects.recipes.prioritizable.models;

import io.effects.recipes.prioritizable.PrioritizableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an item is reprioritized.
 */
public record WorkReprioritized<ID, P>(ID workId, P previousPriority, P newPriority, Instant occurredAt) implements PrioritizableEvent<ID, P> {
    public WorkReprioritized {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(previousPriority);
        Objects.requireNonNull(newPriority);
        Objects.requireNonNull(occurredAt);
    }
}
