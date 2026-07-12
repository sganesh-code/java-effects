package io.effects.recipes.escalatable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the escalatable recipe.
 */
public interface EscalatableEvent<ID, T> {

    /**
     * Unique identifier of the escalatable case.
     */
    ID caseId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
