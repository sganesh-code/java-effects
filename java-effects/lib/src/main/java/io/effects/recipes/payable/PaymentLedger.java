package io.effects.recipes.payable;

import io.effects.Either;
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing the current payment status,
 * authorized amount, captured amount, refunded amount, and chronological transaction steps.
 * It is an Aggregate Root that encapsulates all payment invariants and produces PaymentEvent occurrences.
 */
public final class PaymentLedger {
    public enum Status { INITIAL, AUTHORIZED, CAPTURED, REVERSED, REFUNDED, PARTIALLY_REFUNDED }

    private final String paymentId;
    private Status status = Status.INITIAL;
    private double authorizedAmount = 0.0;
    private double capturedAmount = 0.0;
    private double refundedAmount = 0.0;
    private String currency;
    private final List<PaymentStep> history = new ArrayList<>();

    public PaymentLedger(String paymentId) {
        this.paymentId = Objects.requireNonNull(paymentId);
    }

    public synchronized String paymentId() { return paymentId; }
    public synchronized Status status() { return status; }
    public synchronized double authorizedAmount() { return authorizedAmount; }
    public synchronized double capturedAmount() { return capturedAmount; }
    public synchronized double refundedAmount() { return refundedAmount; }
    public synchronized String currency() { return currency; }
    public synchronized List<PaymentStep> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }

    public synchronized boolean isTerminal() {
        return status == Status.REVERSED || (status == Status.REFUNDED && refundedAmount == capturedAmount);
    }

    /**
     * Records a payment flow step and transitions state internally.
     */
    private synchronized void recordStep(
        PaymentStep step, 
        Status nextStatus, 
        double authDiff, 
        double captureDiff, 
        double refundDiff, 
        String currency
    ) {
        Objects.requireNonNull(step);
        Objects.requireNonNull(nextStatus);

        if (isTerminal()) {
            throw new IllegalStateException("Cannot record a transaction on a terminal payment ledger: " + paymentId);
        }

        this.history.add(step);
        this.status = nextStatus;
        this.authorizedAmount += authDiff;
        this.capturedAmount += captureDiff;
        this.refundedAmount += refundDiff;
        if (currency != null) {
            this.currency = currency;
        }
    }

    /**
     * Behavioral Factory: Evaluates, authorizes, and creates the PaymentLedger.
     */
    public static Either<String, TransitionResult<PaymentLedger, PaymentEvent>> authorize(
        String paymentId, 
        String actorId, 
        double amount, 
        String currency, 
        PayableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        PaymentLedger ledger = new PaymentLedger(paymentId);

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateAuthorization(ledger, amount, currency, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        PaymentStep step = new PaymentStep(
            UUID.randomUUID().toString(),
            actorId,
            PaymentStep.Type.AUTHORIZE,
            amount,
            "Payment authorized",
            now
        );
        ledger.recordStep(step, Status.AUTHORIZED, amount, 0.0, 0.0, currency);

        PaymentEvent event = new PaymentAuthorized(paymentId, amount, currency, now);
        return Either.right(new TransitionResult<>(ledger, event));
    }

    /**
     * Behavioral Transition: Evaluates and captures an authorized payment.
     */
    public synchronized Either<String, PaymentEvent> capture(
        String actorId, 
        double amount, 
        String comment, 
        PayableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status == Status.CAPTURED && authorizedAmount == 0.0) {
            return Either.right(null); // Idempotent success
        }
        if (status != Status.AUTHORIZED && status != Status.CAPTURED) {
            return Either.left("Cannot capture payment in current status: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateCapture(this, amount, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        PaymentStep step = new PaymentStep(
            UUID.randomUUID().toString(),
            actorId,
            PaymentStep.Type.CAPTURE,
            amount,
            comment,
            now
        );
        recordStep(step, Status.CAPTURED, -amount, amount, 0.0, null);

        PaymentEvent event = new PaymentCaptured(paymentId, amount, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Reverses/voids an active payment authorization.
     */
    public synchronized Either<String, PaymentEvent> reverse(
        String actorId, 
        String reason, 
        PayableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status == Status.REVERSED) {
            return Either.right(null); // Idempotent success
        }
        if (status != Status.AUTHORIZED) {
            return Either.left("Cannot reverse: payment status is not AUTHORIZED (current status: " + status + ")");
        }

        // Domain validation
        Either<String, Void> eitherValid = request.evaluateReversal(this, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        PaymentStep step = new PaymentStep(
            UUID.randomUUID().toString(),
            actorId,
            PaymentStep.Type.REVERSE,
            authorizedAmount,
            reason,
            now
        );
        double originalAuth = authorizedAmount;
        recordStep(step, Status.REVERSED, -originalAuth, 0.0, 0.0, null);

        PaymentEvent event = new PaymentReversed(paymentId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Refunds a captured payment.
     */
    public synchronized Either<String, PaymentEvent> refund(
        String actorId, 
        double amount, 
        String reason, 
        PayableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.CAPTURED && status != Status.PARTIALLY_REFUNDED) {
            return Either.left("Cannot refund payment in current status: " + status);
        }

        // Domain validation
        Either<String, Void> eitherValid = request.evaluateRefund(this, amount, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        PaymentStep step = new PaymentStep(
            UUID.randomUUID().toString(),
            actorId,
            PaymentStep.Type.REFUND,
            amount,
            reason,
            now
        );

        double nextRefunded = refundedAmount + amount;
        Status nextStatus = nextRefunded >= capturedAmount ? Status.REFUNDED : Status.PARTIALLY_REFUNDED;

        recordStep(step, nextStatus, 0.0, 0.0, amount, null);

        PaymentEvent event = new PaymentRefunded(paymentId, amount, now);
        return Either.right(event);
    }
}
