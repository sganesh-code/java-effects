package io.effects.recipes.reconciliable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the reconciliation recipe.
 */
public interface ReconciliationEvent<ID, K> {

    /**
     * Unique identifier of the reconciliation ledger.
     */
    ID reconciliationId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
