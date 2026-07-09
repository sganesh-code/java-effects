package io.effects.recipes.schedulable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an occurrence trigger time is successfully adjusted/rescheduled.
 */
public final class OccurrenceRescheduled implements SchedulableEvent {
    private final String occurrenceId;
    private final Instant triggerTime;
    private final Instant occurredAt;

    public OccurrenceRescheduled(String occurrenceId, Instant triggerTime, Instant occurredAt) {
        this.occurrenceId = Objects.requireNonNull(occurrenceId);
        this.triggerTime = Objects.requireNonNull(triggerTime);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String occurrenceId() { return occurrenceId; }

    public Instant triggerTime() { return triggerTime; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
