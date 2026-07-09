package io.effects.recipes.payable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a payment is successfully authorized.
 */
public record PaymentAuthorized(String paymentId, double amount, String currency,
                                Instant occurredAt) implements PaymentEvent {
    public PaymentAuthorized(String paymentId, double amount, String currency, Instant occurredAt) {
        this.paymentId = Objects.requireNonNull(paymentId);
        this.amount = amount;
        this.currency = Objects.requireNonNull(currency);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
}
