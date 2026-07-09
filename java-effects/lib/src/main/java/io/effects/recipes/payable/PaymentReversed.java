package io.effects.recipes.payable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a payment authorization is reversed/cancelled.
 */
public record PaymentReversed<ID, M>(ID paymentId, Instant occurredAt) implements PaymentEvent<ID, M> {
    public PaymentReversed(ID paymentId, Instant occurredAt) {
        this.paymentId = Objects.requireNonNull(paymentId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
}
