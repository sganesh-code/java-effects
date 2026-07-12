package io.effects.recipes.reconciliable.models;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable step in the reconciliation audit trail.
 */
public record ReconciliationStep<K, C>(String stepId, K itemId, Type type, C comment, Instant timestamp) {
    public enum Type { MATCH, DISCREPANCY, RESOLVE }

    public ReconciliationStep {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(timestamp);
    }
}
