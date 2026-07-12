package io.effects.recipes.escalatable.models;

import io.effects.recipes.escalatable.EscalatableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a case has been promoted or escalated to a higher authority tier.
 */
public record CaseEscalated<ID, T>(ID caseId, T escalatedTier, Instant occurredAt) implements EscalatableEvent<ID, T> {
    public CaseEscalated {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(escalatedTier);
        Objects.requireNonNull(occurredAt);
    }
}
