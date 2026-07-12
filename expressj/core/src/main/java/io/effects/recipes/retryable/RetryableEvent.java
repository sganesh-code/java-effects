package io.effects.recipes.retryable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the retryable recipe.
 */
public interface RetryableEvent<ID> {

    /**
     * Unique identifier of the retryable operation.
     */
    ID operationId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
