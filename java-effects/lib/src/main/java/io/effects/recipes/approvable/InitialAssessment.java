package io.effects.recipes.approvable;

import java.util.Objects;

/**
 * Result of evaluating an initial submission.
 */
public record InitialAssessment(Status initialStatus, String requiredAuthority) {
    public InitialAssessment(Status initialStatus, String requiredAuthority) {
        this.initialStatus = Objects.requireNonNull(initialStatus);
        this.requiredAuthority = requiredAuthority;
    }
}
