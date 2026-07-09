package io.effects.recipes.payable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a captured payment is refunded.
 */
public record PaymentRefunded(String paymentId, double amount, Instant occurredAt) implements PaymentEvent {
    public PaymentRefunded(String paymentId, double amount, Instant occurredAt) {
        this.paymentId = Objects.requireNonNull(paymentId);
        this.amount = amount;
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
}
