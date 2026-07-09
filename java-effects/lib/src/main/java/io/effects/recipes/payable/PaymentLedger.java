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
 * A rich, non-anemic domain state ledger representing the current payment status
 * and chronological transaction steps.
 * It is an Aggregate Root that encapsulates all payment invariants and produces PaymentEvent occurrences.
 */
public final class PaymentLedger<ID, M> {
    public enum Status { INITIAL, AUTHORIZED, CAPTURED, REVERSED, REFUNDED, PARTIALLY_REFUNDED }

    private final ID paymentId;
    private Status status = Status.INITIAL;
    private final List<PaymentStep<M>> history = new ArrayList<>();

    public PaymentLedger(ID paymentId) {
        this.paymentId = Objects.requireNonNull(paymentId);
    }

    public synchronized ID paymentId() { return paymentId; }
    public synchronized Status status() { return status; }
    public synchronized List<PaymentStep<M>> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }

    public synchronized boolean isTerminal() {
        return status == Status.REVERSED || status == Status.REFUNDED;
    }

    /**
     * Records a payment flow step and transitions state internally.
     */
    private synchronized void recordStep(PaymentStep<M> step, Status nextStatus) {
        Objects.requireNonNull(step);
        Objects.requireNonNull(nextStatus);

        if (isTerminal()) {
            throw new IllegalStateException("Cannot record a transaction on a terminal payment ledger: " + paymentId);
        }

        this.history.add(step);
        this.status = nextStatus;
    }

    /**
     * Behavioral Factory: Evaluates, authorizes, and creates the PaymentLedger.
     */
    public static <ID, M> Either<String, TransitionResult<PaymentLedger<ID, M>, PaymentEvent<ID, M>>> authorize(
        ID paymentId, 
        String actorId, 
        M detail, 
        PayableRequest<ID, M> request, 
        Instant now
    ) {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        PaymentLedger<ID, M> ledger = new PaymentLedger<>(paymentId);

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateAuthorization(ledger, detail, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        PaymentStep<M> step = new PaymentStep<>(
            UUID.randomUUID().toString(),
            actorId,
            PaymentStep.Type.AUTHORIZE,
            detail,
            "Payment authorized",
            now
        );
        ledger.recordStep(step, Status.AUTHORIZED);

        PaymentEvent<ID, M> event = new PaymentAuthorized<>(paymentId, detail, now);
        return Either.right(new TransitionResult<>(ledger, event));
    }

    /**
     * Behavioral Transition: Evaluates and captures an authorized payment.
     */
    public synchronized Either<String, PaymentEvent<ID, M>> capture(
        String actorId, 
        M detail, 
        String comment, 
        PayableRequest<ID, M> request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status == Status.CAPTURED) {
            return Either.right(null); // Idempotent success
        }
        if (status != Status.AUTHORIZED) {
            return Either.left("Cannot capture payment in current status: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateCapture(this, detail, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        PaymentStep<M> step = new PaymentStep<>(
            UUID.randomUUID().toString(),
            actorId,
            PaymentStep.Type.CAPTURE,
            detail,
            comment,
            now
        );
        recordStep(step, Status.CAPTURED);

        PaymentEvent<ID, M> event = new PaymentCaptured<>(paymentId, detail, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Reverses/voids an active payment authorization.
     */
    public synchronized Either<String, PaymentEvent<ID, M>> reverse(
        String actorId, 
        String reason, 
        PayableRequest<ID, M> request, 
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

        M detail = history.stream()
            .filter(s -> s.type() == PaymentStep.Type.AUTHORIZE)
            .map(PaymentStep::detail)
            .findFirst()
            .orElse(null);

        PaymentStep<M> step = new PaymentStep<>(
            UUID.randomUUID().toString(),
            actorId,
            PaymentStep.Type.REVERSE,
            detail,
            reason,
            now
        );
        recordStep(step, Status.REVERSED);

        PaymentEvent<ID, M> event = new PaymentReversed<>(paymentId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Refunds a captured payment.
     */
    public synchronized Either<String, PaymentEvent<ID, M>> refund(
        String actorId, 
        M detail, 
        String reason, 
        PayableRequest<ID, M> request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.CAPTURED && status != Status.PARTIALLY_REFUNDED) {
            return Either.left("Cannot refund payment in current status: " + status);
        }

        // Domain validation
        Either<String, Status> eitherValid = request.evaluateRefund(this, detail, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        Status nextStatus = eitherValid.getRight();
        if (nextStatus != Status.REFUNDED && nextStatus != Status.PARTIALLY_REFUNDED) {
            return Either.left("Invalid refund transition status returned by evaluation: " + nextStatus);
        }

        PaymentStep<M> step = new PaymentStep<>(
            UUID.randomUUID().toString(),
            actorId,
            PaymentStep.Type.REFUND,
            detail,
            reason,
            now
        );

        recordStep(step, nextStatus);

        PaymentEvent<ID, M> event = new PaymentRefunded<>(paymentId, detail, now);
        return Either.right(event);
    }
}