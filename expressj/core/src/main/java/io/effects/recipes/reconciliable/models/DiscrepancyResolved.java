package io.effects.recipes.reconciliable.models;

import io.effects.recipes.reconciliable.ReconciliationEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a previously flagged discrepancy is successfully resolved.
 */
public record DiscrepancyResolved<ID, K>(ID reconciliationId, K itemId, String resolutionType, Instant occurredAt) implements ReconciliationEvent<ID, K> {
    public DiscrepancyResolved {
        Objects.requireNonNull(reconciliationId);
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(resolutionType);
        Objects.requireNonNull(occurredAt);
    }
}
