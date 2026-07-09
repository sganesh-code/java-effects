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
public interface FulfillableRequest<ID, Q> {

    /**
     * Behavioral Message: Evaluates whether an initial or partial allocation is allowed.
     */
    Either<String, Void> evaluateAllocation(FulfillmentLedger<ID, Q> ledger, Q detail, Instant now);

    /**
     * Behavioral Message: Evaluates whether packaging of allocated items is allowed.
     */
    Either<String, Void> evaluatePackaging(FulfillmentLedger<ID, Q> ledger, Q detail, Instant now);

    /**
     * Behavioral Message: Evaluates whether dispatch (shipping) is allowed.
     */
    Either<String, Void> evaluateDispatch(FulfillmentLedger<ID, Q> ledger, Instant now);

    /**
     * Behavioral Message: Evaluates whether delivery completion is allowed.
     */
    Either<String, Void> evaluateCompletion(FulfillmentLedger<ID, Q> ledger, Instant now);

    /**
     * Behavioral Message: Evaluates whether release of allocated/packaged items is allowed.
     * Returns the next status on the Right, allowing the consumer to determine if the fulfillment
     * reverts to INITIAL or stays in its current state.
     */
    Either<String, FulfillmentLedger.Status> evaluateRelease(FulfillmentLedger<ID, Q> ledger, Q detail, Instant now);
}