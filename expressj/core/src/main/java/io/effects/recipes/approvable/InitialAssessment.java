package io.effects.recipes.approvable;

import java.util.Objects;

/**
 * Result of evaluating an initial submission.
 */
public record InitialAssessment<A>(Status initialStatus, A requiredAuthority) {
    public InitialAssessment(Status initialStatus, A requiredAuthority) {
        this.initialStatus = Objects.requireNonNull(initialStatus);
        this.requiredAuthority = requiredAuthority;
    }
}