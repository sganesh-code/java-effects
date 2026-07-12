package io.effects.recipes.reconciliable.models;

import io.effects.recipes.reconciliable.ReconciliationEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an item has been successfully matched.
 */
public record ItemMatched<ID, K>(ID reconciliationId, K itemId, Instant occurredAt) implements ReconciliationEvent<ID, K> {
    public ItemMatched {
        Objects.requireNonNull(reconciliationId);
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(occurredAt);
    }
}
