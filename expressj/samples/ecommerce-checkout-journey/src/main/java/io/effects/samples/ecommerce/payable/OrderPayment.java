package io.effects.samples.ecommerce.payable;

import io.effects.Either;
import io.effects.recipes.payable.PayableRequest;
import io.effects.recipes.payable.PaymentLedger;
import java.time.Instant;

public class OrderPayment implements PayableRequest<String, Double> {

    @Override
    public Either<String, Void> evaluateAuthorization(PaymentLedger<String, Double> ledger, Double amount, Instant now) {
        if (amount <= 0.0) {
            return Either.left("Payment authorization amount must be positive.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateCapture(PaymentLedger<String, Double> ledger, Double amount, Instant now) {
        if (amount <= 0.0) {
            return Either.left("Payment capture amount must be positive.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, PaymentLedger.Status> evaluateRefund(PaymentLedger<String, Double> ledger, Double amount, Instant now) {
        if (amount <= 0.0) {
            return Either.left("Refund amount must be positive.");
        }
        return Either.right(PaymentLedger.Status.REFUNDED);
    }

    @Override
    public Either<String, Void> evaluateReversal(PaymentLedger<String, Double> ledger, Instant now) {
        return Either.right(null);
    }
}
