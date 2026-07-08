package io.effects.recipes.approvable;

import java.util.Objects;

/**
 * Result of evaluating an initial submission.
 */
public final class InitialAssessment {
    private final Status initialStatus;
    private final String requiredAuthority;

    public InitialAssessment(Status initialStatus, String requiredAuthority) {
        this.initialStatus = Objects.requireNonNull(initialStatus);
        this.requiredAuthority = requiredAuthority;
    }

    public Status initialStatus() { return initialStatus; }
    public String requiredAuthority() { return requiredAuthority; }
}
