package io.effects.recipes.meterable.models;

import io.effects.recipes.meterable.*;
import io.effects.recipes.meterable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable register representing a single, discrete usage consumption tick.
 */
public record UsageStep<U>(
    String stepId,
    U metric,
    Instant timestamp
) {
    public UsageStep {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(metric);
        Objects.requireNonNull(timestamp);
    }
}