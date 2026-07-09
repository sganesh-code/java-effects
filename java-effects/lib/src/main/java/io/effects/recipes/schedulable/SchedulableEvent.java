package io.effects.recipes.schedulable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the schedulable recipe.
 */
public interface SchedulableEvent {

    /**
     * Unique identifier of the scheduled occurrence.
     */
    String occurrenceId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
