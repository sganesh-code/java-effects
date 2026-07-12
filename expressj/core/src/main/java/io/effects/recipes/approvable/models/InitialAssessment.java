package io.effects.recipes.approvable.models;

import io.effects.recipes.approvable.*;
import io.effects.recipes.approvable.models.*;

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