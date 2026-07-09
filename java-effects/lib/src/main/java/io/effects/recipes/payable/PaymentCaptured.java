package io.effects.recipes.payable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a payment is successfully captured.
 */
public record PaymentCaptured(String paymentId, double amount, Instant occurredAt) implements PaymentEvent {
    public PaymentCaptured(String paymentId, double amount, Instant occurredAt) {
        this.paymentId = Objects.requireNonNull(paymentId);
        this.amount = amount;
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
}
