package io.effects.recipes.ports.approvable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a request is successfully and fully approved.
 */
public final class RequestApproved implements ApprovalEvent {
    private final String requestId;
    private final String approverId;
    private final String comment;
    private final Instant occurredAt;

    public RequestApproved(String requestId, String approverId, String comment, Instant occurredAt) {
        this.requestId = Objects.requireNonNull(requestId);
        this.approverId = Objects.requireNonNull(approverId);
        this.comment = Objects.requireNonNull(comment);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String requestId() { return requestId; }

    public String approverId() { return approverId; }

    public String comment() { return comment; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
