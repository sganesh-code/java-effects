package io.effects.recipes.escalatable.models;

import io.effects.recipes.escalatable.EscalatableEvent;
import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a case has been reassigned to a new handler/specialist.
 */
public record CaseReassigned<ID, T>(ID caseId, String previousHandlerId, String newHandlerId, Instant occurredAt) implements EscalatableEvent<ID, T> {
    public CaseReassigned {
        Objects.requireNonNull(caseId);
        Objects.requireNonNull(previousHandlerId);
        Objects.requireNonNull(newHandlerId);
        Objects.requireNonNull(occurredAt);
    }
}
