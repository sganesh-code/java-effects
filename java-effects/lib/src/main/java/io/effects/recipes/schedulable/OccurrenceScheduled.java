package io.effects.recipes.schedulable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an occurrence is successfully scheduled.
 */
public final class OccurrenceScheduled implements SchedulableEvent {
    private final String occurrenceId;
    private final Instant triggerTime;
    private final Instant occurredAt;

    public OccurrenceScheduled(String occurrenceId, Instant triggerTime, Instant occurredAt) {
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
