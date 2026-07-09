package io.effects.recipes.fulfillable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when the fulfillment order is successfully delivered/completed.
 */
public final class FulfillmentCompleted implements FulfillmentEvent {
    private final String fulfillmentId;
    private final Instant occurredAt;

    public FulfillmentCompleted(String fulfillmentId, Instant occurredAt) {
        this.fulfillmentId = Objects.requireNonNull(fulfillmentId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String fulfillmentId() { return fulfillmentId; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
