package io.effects.recipes.fulfillable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when allocated or packaged items are released back to capacity.
 */
public final class FulfillmentReleased implements FulfillmentEvent {
    private final String fulfillmentId;
    private final int quantity;
    private final Instant occurredAt;

    public FulfillmentReleased(String fulfillmentId, int quantity, Instant occurredAt) {
        this.fulfillmentId = Objects.requireNonNull(fulfillmentId);
        this.quantity = quantity;
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String fulfillmentId() { return fulfillmentId; }

    public int quantity() { return quantity; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
