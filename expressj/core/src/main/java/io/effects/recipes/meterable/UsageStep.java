package io.effects.recipes.meterable;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable record representing a single, discrete usage consumption tick.
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