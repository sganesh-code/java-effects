package io.effects.recipes.escalatable.models;

import io.effects.recipes.escalatable.EscalatableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an SLA threshold or warning is triggered.
 */
public record SLAWarningTriggered<ID, T>(ID caseId, Instant occurredAt) implements EscalatableEvent<ID, T> {
    public SLAWarningTriggered {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(occurredAt);
    }
}
