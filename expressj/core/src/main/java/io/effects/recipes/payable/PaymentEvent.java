package io.effects.recipes.payable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the payment recipe.
 */
public interface PaymentEvent<ID, M> {

    /**
     * Unique identifier of the payment.
     */
    ID paymentId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
