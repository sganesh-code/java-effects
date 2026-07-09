package io.effects.recipes.fulfillable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when the fulfillment order is dispatched/shipped.
 */
public final class FulfillmentDispatched<ID, Q> implements FulfillmentEvent<ID, Q> {
    private final ID fulfillmentId;
    private final Instant occurredAt;

    public FulfillmentDispatched(ID fulfillmentId, Instant occurredAt) {
        this.fulfillmentId = Objects.requireNonNull(fulfillmentId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public ID fulfillmentId() { return fulfillmentId; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}