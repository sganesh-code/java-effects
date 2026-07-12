package io.effects.recipes.retryable;

import io.effects.recipes.retryable.models.*;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing an operation with retry capabilities.
 *
 * It contains zero passive getters/setters and executes purely and synchronously.
 */
public interface RetryableRequest<ID, C> {

    /**
     * Determines if the given error message is transient (retryable) or fatal (permanent).
     * Returns true if retryable, false if permanent.
     */
    boolean isTransientFailure(Throwable error);

    /**
     * Calculates the backoff delay in milliseconds for the next retry attempt.
     */
    long calculateBackoffMillis(int currentAttempt, Instant now);

    /**
     * Determines the maximum number of retry attempts permitted before abandonment.
     */
    int maxAttempts();
}
