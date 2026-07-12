package io.effects.recipes.schedulable.models;

import io.effects.recipes.schedulable.*;
import io.effects.recipes.schedulable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an occurrence is fired/executed.
 */
public final class OccurrenceFired<ID, T> implements SchedulableEvent<ID, T> {
    private final ID occurrenceId;
    private final Instant occurredAt;

    public OccurrenceFired(ID occurrenceId, Instant occurredAt) {
        this.occurrenceId = Objects.requireNonNull(occurrenceId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public ID occurrenceId() { return occurrenceId; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}