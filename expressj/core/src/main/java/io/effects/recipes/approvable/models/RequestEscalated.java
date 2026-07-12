package io.effects.recipes.approvable.models;

import io.effects.recipes.approvable.*;
import io.effects.recipes.approvable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a request is escalated to a higher required authority.
 */
public final class RequestEscalated<ID, A> implements ApprovalEvent<ID, A> {
    private final ID requestId;
    private final String escalatorId;
    private final A targetAuthority;
    private final String reason;
    private final Instant occurredAt;

    public RequestEscalated(ID requestId, String escalatorId, A targetAuthority, String reason, Instant occurredAt) {
        this.requestId = Objects.requireNonNull(requestId);
        this.escalatorId = Objects.requireNonNull(escalatorId);
        this.targetAuthority = targetAuthority;
        this.reason = Objects.requireNonNull(reason);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public ID requestId() { return requestId; }

    public String escalatorId() { return escalatorId; }

    public A targetAuthority() { return targetAuthority; }

    public String reason() { return reason; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}