package io.effects.recipes.meterable;

import io.effects.recipes.meterable.models.*;

import io.effects.core.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing a metered usage and billing rule scheme.
 * It contains NO passive getters, relying entirely on double-dispatch / object collaboration.
 */
public interface MeterableRequest<ID, U, R> {

    /**
     * Behavioral Message: Evaluates whether a proposed usage consumption tick is valid.
     */
    Either<String, Void> evaluateUsage(MeterLedger<ID, U> ledger, U metric, Instant now);

    /**
     * Behavioral Message: Rates the current cycle's recorded usage and computes pricing invoice R.
     */
    Either<String, R> evaluateRating(MeterLedger<ID, U> ledger, Instant now);
}