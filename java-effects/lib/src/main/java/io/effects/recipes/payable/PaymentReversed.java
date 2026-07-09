package io.effects.recipes.payable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a payment authorization is reversed/cancelled.
 */
public record PaymentReversed(String paymentId, Instant occurredAt) implements PaymentEvent {
    public PaymentReversed(String paymentId, Instant occurredAt) {
        this.paymentId = Objects.requireNonNull(paymentId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
}
