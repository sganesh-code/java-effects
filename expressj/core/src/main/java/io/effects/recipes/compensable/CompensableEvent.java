package io.effects.recipes.compensable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the compensable recipe.
 */
public interface CompensableEvent<ID> {

    /**
     * Unique identifier of the Saga transaction.
     */
    ID transactionId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
