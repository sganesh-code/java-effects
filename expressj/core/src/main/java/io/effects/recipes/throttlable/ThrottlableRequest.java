package io.effects.recipes.throttlable;

import io.effects.recipes.throttlable.models.*;
import io.effects.core.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing rate-limiting settings for an actor.
 *
 * It contains zero passive getters/setters and executes purely and synchronously.
 */
public interface ThrottlableRequest<ID, C> {

    /**
     * The maximum capacity of the token bucket.
     */
    double maxCapacity();

    /**
     * The number of tokens refilled per millisecond.
     */
    double refillRatePerMillis();

    /**
     * Evaluates whether proposed token consumption is valid under current quota rules.
     */
    Either<String, Void> evaluateConsume(TokenBucketLedger<ID, C> ledger, double requestedTokens, Instant now);

    /**
     * Evaluates whether a quota refill is allowed.
     */
    Either<String, Void> evaluateRefill(TokenBucketLedger<ID, C> ledger, double refilledTokens, Instant now);
}
