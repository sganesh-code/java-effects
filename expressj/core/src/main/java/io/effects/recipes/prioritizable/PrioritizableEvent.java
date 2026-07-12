package io.effects.recipes.prioritizable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the prioritizable recipe.
 */
public interface PrioritizableEvent<ID, P> {

    /**
     * Unique identifier of the prioritizable work.
     */
    ID workId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
