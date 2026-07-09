package io.effects.recipes.payable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A non-anemic, thread-safe domain state ledger representing the current payment status,
 * authorized amount, captured amount, refunded amount, and chronological transaction steps.
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
     * Records a payment flow step and transitions state.
     * Enforces that no further steps can be appended if the record has reached a terminal state.
     */
    public synchronized void recordStep(
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
}
