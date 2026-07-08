package io.effects.recipes.approvable;

import java.util.Objects;

/**
 * Result of evaluating a decision.
 */
public record NextStep(Status nextStatus, String nextRequiredAuthority) {
    public NextStep(Status nextStatus, String nextRequiredAuthority) {
        this.nextStatus = Objects.requireNonNull(nextStatus);
        this.nextRequiredAuthority = nextRequiredAuthority;
    }
}
