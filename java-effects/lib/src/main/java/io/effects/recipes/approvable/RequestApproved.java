package io.effects.recipes.approvable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a request is successfully and fully approved.
 */
public final class RequestApproved<ID, A> implements ApprovalEvent<ID, A> {
    private final ID requestId;
    private final String approverId;
    private final String comment;
    private final Instant occurredAt;

    public RequestApproved(ID requestId, String approverId, String comment, Instant occurredAt) {
        this.requestId = Objects.requireNonNull(requestId);
        this.approverId = Objects.requireNonNull(approverId);
        this.comment = Objects.requireNonNull(comment);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public ID requestId() { return requestId; }

    public String approverId() { return approverId; }

    public String comment() { return comment; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}