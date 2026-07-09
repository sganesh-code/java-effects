package io.effects.recipes.payable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a captured payment is refunded.
 */
public final class PaymentRefunded implements PaymentEvent {
    private final String paymentId;
    private final double amount;
    private final Instant occurredAt;

    public PaymentRefunded(String paymentId, double amount, Instant occurredAt) {
        this.paymentId = Objects.requireNonNull(paymentId);
        this.amount = amount;
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String paymentId() { return paymentId; }

    public double amount() { return amount; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
