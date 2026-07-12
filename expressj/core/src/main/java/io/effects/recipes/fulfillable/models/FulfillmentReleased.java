package io.effects.recipes.fulfillable.models;

import io.effects.recipes.fulfillable.*;
import io.effects.recipes.fulfillable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when allocated or packaged items are released back to capacity.
 */
public final class FulfillmentReleased<ID, Q> implements FulfillmentEvent<ID, Q> {
    private final ID fulfillmentId;
    private final Q detail;
    private final Instant occurredAt;

    public FulfillmentReleased(ID fulfillmentId, Q detail, Instant occurredAt) {
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