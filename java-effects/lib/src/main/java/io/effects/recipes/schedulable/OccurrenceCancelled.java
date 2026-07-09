package io.effects.recipes.schedulable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an occurrence is successfully cancelled.
 */
public final class OccurrenceCancelled implements SchedulableEvent {
    private final String occurrenceId;
    private final Instant occurredAt;

    public OccurrenceCancelled(String occurrenceId, Instant occurredAt) {
        this.occurrenceId = Objects.requireNonNull(occurrenceId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String occurrenceId() { return occurrenceId; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
