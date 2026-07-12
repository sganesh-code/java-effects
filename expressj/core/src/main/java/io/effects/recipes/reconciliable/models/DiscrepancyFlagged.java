package io.effects.recipes.reconciliable.models;

import io.effects.recipes.reconciliable.ReconciliationEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a mismatch or discrepancy is flagged.
 */
public record DiscrepancyFlagged<ID, K>(ID reconciliationId, K itemId, String discrepancyCode, Instant occurredAt) implements ReconciliationEvent<ID, K> {
    public DiscrepancyFlagged {
        Objects.requireNonNull(reconciliationId);
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(discrepancyCode);
        Objects.requireNonNull(occurredAt);
    }
}
