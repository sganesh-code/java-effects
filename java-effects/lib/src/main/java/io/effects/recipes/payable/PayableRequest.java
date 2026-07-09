package io.effects.recipes.payable;

import io.effects.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing a payment request.
 * 
 * In this design, the consumer's implementation is completely synchronous and pure!
 * It contains NO monadic references (no IO) or threading knowledge.
 * The monadic shell (PayableProcess) is responsible for lifting these pure synchronous
 * evaluations safely into the lazy, concurrent IO context.
 */
public interface PayableRequest<ID, M> {

    /**
     * Behavioral Message: Evaluates whether an initial payment authorization is allowed.
     */
    Either<String, Void> evaluateAuthorization(PaymentLedger<ID, M> ledger, M detail, Instant now);

    /**
     * Behavioral Message: Evaluates a proposed capture against the ledger.
     */
    Either<String, Void> evaluateCapture(PaymentLedger<ID, M> ledger, M detail, Instant now);

    /**
     * Behavioral Message: Evaluates a proposed reversal of an authorization.
     */
    Either<String, Void> evaluateReversal(PaymentLedger<ID, M> ledger, Instant now);

    /**
     * Behavioral Message: Evaluates a proposed refund against the ledger.
     */
    Either<String, PaymentLedger.Status> evaluateRefund(PaymentLedger<ID, M> ledger, M detail, Instant now);
}
