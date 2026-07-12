package io.effects.recipes.approvable.models;

import io.effects.recipes.approvable.*;
import io.effects.recipes.approvable.models.*;

import java.util.Objects;

/**
 * Result of evaluating a decision.
 */
public record NextStep<A>(Status nextStatus, A nextRequiredAuthority) {
    public NextStep(Status nextStatus, A nextRequiredAuthority) {
        this.nextStatus = Objects.requireNonNull(nextStatus);
        this.nextRequiredAuthority = nextRequiredAuthority;
    }
}