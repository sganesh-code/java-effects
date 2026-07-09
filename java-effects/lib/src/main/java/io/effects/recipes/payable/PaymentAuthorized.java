package io.effects.recipes.payable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a payment is successfully authorized.
 */
public final class PaymentAuthorized implements PaymentEvent {
    private final String paymentId;
    private final double amount;
    private final String currency;
    private final Instant occurredAt;

    public PaymentAuthorized(String paymentId, double amount, String currency, Instant occurredAt) {
        this.paymentId = Objects.requireNonNull(paymentId);
        this.amount = amount;
        this.currency = Objects.requireNonNull(currency);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String paymentId() { return paymentId; }

    public double amount() { return amount; }

    public String currency() { return currency; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
