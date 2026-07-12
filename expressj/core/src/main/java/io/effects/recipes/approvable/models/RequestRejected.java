package io.effects.recipes.approvable.models;

import io.effects.recipes.approvable.*;
import io.effects.recipes.approvable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a request is rejected.
 */
public final class RequestRejected<ID, A> implements ApprovalEvent<ID, A> {
    private final ID requestId;
    private final String rejecterId;
    private final String reason;
    private final Instant occurredAt;

    public RequestRejected(ID requestId, String rejecterId, String reason, Instant occurredAt) {
        this.requestId = Objects.requireNonNull(requestId);
        this.rejecterId = Objects.requireNonNull(rejecterId);
        this.reason = Objects.requireNonNull(reason);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public ID requestId() { return requestId; }

    public String rejecterId() { return rejecterId; }

    public String reason() { return reason; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}