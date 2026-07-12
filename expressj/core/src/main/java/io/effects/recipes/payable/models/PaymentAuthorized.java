package io.effects.recipes.payable.models;

import io.effects.recipes.payable.*;
import io.effects.recipes.payable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a payment is successfully authorized.
 */
public record PaymentAuthorized<ID, M>(ID paymentId, M detail, Instant occurredAt) implements PaymentEvent<ID, M> {
    public PaymentAuthorized(ID paymentId, M detail, Instant occurredAt) {
        this.paymentId = Objects.requireNonNull(paymentId);
        this.detail = Objects.requireNonNull(detail);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
}
