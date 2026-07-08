package io.effects.recipes.approvable;

import java.util.Objects;

/**
 * Result of evaluating a decision.
 */
public final class NextStep {
    private final Status nextStatus;
    private final String nextRequiredAuthority;

    public NextStep(Status nextStatus, String nextRequiredAuthority) {
        this.nextStatus = Objects.requireNonNull(nextStatus);
        this.nextRequiredAuthority = nextRequiredAuthority;
    }

    public Status nextStatus() { return nextStatus; }
    public String nextRequiredAuthority() { return nextRequiredAuthority; }
}
