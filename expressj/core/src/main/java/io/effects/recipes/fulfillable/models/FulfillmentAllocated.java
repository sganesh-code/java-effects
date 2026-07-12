package io.effects.recipes.fulfillable.models;

import io.effects.recipes.fulfillable.*;
import io.effects.recipes.fulfillable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when items are successfully allocated.
 */
public final class FulfillmentAllocated<ID, Q> implements FulfillmentEvent<ID, Q> {
    private final ID fulfillmentId;
    private final Q detail;
    private final Instant occurredAt;

    public FulfillmentAllocated(ID fulfillmentId, Q detail, Instant occurredAt) {
        this.fulfillmentId = Objects.requireNonNull(fulfillmentId);
        this.detail = Objects.requireNonNull(detail);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public ID fulfillmentId() { return fulfillmentId; }

    public Q detail() { return detail; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}