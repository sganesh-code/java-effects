package io.effects.recipes.schedulable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an occurrence is successfully scheduled.
 */
public final class OccurrenceScheduled<ID, T> implements SchedulableEvent<ID, T> {
    private final ID occurrenceId;
    private final T triggerTime;
    private final Instant occurredAt;

    public OccurrenceScheduled(ID occurrenceId, T triggerTime, Instant occurredAt) {
        this.occurrenceId = Objects.requireNonNull(occurrenceId);
        this.triggerTime = Objects.requireNonNull(triggerTime);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public ID occurrenceId() { return occurrenceId; }

    public T triggerTime() { return triggerTime; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}