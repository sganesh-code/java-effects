package io.effects.recipes.prioritizable.models;

import io.effects.recipes.prioritizable.PrioritizableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when initial priority sequencing is completed.
 */
public record WorkSequenced<ID, P>(ID workId, P priority, Instant occurredAt) implements PrioritizableEvent<ID, P> {
    public WorkSequenced {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(priority);
        Objects.requireNonNull(occurredAt);
    }
}
