package io.effects.recipes.fulfillable;

import io.effects.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing a fulfillment request.
 * 
 * In this design, the consumer's implementation is completely synchronous and pure!
 * It contains NO monadic references (no IO) or threading knowledge.
 * The monadic shell (FulfillmentProcess) is responsible for lifting these pure synchronous
 * evaluations safely into the lazy, concurrent IO context.
 */
public interface FulfillableRequest {

    /**
     * Behavioral Message: Evaluates whether an initial or partial allocation is allowed.
     */
    Either<String, Void> evaluateAllocation(FulfillmentLedger ledger, int quantity, Instant now);

    /**
     * Behavioral Message: Evaluates whether packaging of allocated items is allowed.
     */
    Either<String, Void> evaluatePackaging(FulfillmentLedger ledger, int quantity, Instant now);

    /**
     * Behavioral Message: Evaluates whether dispatch (shipping) is allowed.
     */
    Either<String, Void> evaluateDispatch(FulfillmentLedger ledger, Instant now);

    /**
     * Behavioral Message: Evaluates whether delivery completion is allowed.
     */
    Either<String, Void> evaluateCompletion(FulfillmentLedger ledger, Instant now);

    /**
     * Behavioral Message: Evaluates whether release of allocated/packaged items is allowed.
     */
    Either<String, Void> evaluateRelease(FulfillmentLedger ledger, int quantity, Instant now);
}
