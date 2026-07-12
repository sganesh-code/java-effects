package io.effects.recipes.approvable.models;

import io.effects.recipes.approvable.*;
import io.effects.recipes.approvable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a request is submitted and enters the pending or escalated state.
 */
public final class RequestSubmitted<ID, A> implements ApprovalEvent<ID, A> {
    private final ID requestId;
    private final String initiatorId;
    private final A requiredAuthority;
    private final Instant occurredAt;

    public RequestSubmitted(ID requestId, String initiatorId, A requiredAuthority, Instant occurredAt) {
        this.requestId = Objects.requireNonNull(requestId);
        this.initiatorId = Objects.requireNonNull(initiatorId);
        this.requiredAuthority = requiredAuthority;
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public ID requestId() { return requestId; }

    public String initiatorId() { return initiatorId; }

    public A requiredAuthority() { return requiredAuthority; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}