package io.effects.recipes.payable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a payment authorization is reversed/cancelled.
 */
public final class PaymentReversed implements PaymentEvent {
    private final String paymentId;
    private final Instant occurredAt;

    public PaymentReversed(String paymentId, Instant occurredAt) {
        this.paymentId = Objects.requireNonNull(paymentId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String paymentId() { return paymentId; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
