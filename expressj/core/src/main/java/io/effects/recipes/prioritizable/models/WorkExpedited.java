package io.effects.recipes.prioritizable.models;

import io.effects.recipes.prioritizable.PrioritizableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an item is expedited.
 */
public record WorkExpedited<ID, P>(ID workId, Instant occurredAt) implements PrioritizableEvent<ID, P> {
    public WorkExpedited {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(occurredAt);
    }
}
