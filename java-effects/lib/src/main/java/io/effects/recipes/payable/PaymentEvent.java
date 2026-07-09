package io.effects.recipes.payable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the payment recipe.
 */
public interface PaymentEvent {

    /**
     * Unique identifier of the payment.
     */
    String paymentId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
