package io.effects.recipes.escalatable.models;

import io.effects.recipes.escalatable.EscalatableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a case has been de-escalated to a lower authority tier.
 */
public record CaseDeescalated<ID, T>(ID caseId, T deescalatedTier, Instant occurredAt) implements EscalatableEvent<ID, T> {
    public CaseDeescalated {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(deescalatedTier);
        Objects.requireNonNull(occurredAt);
    }
}
