package io.effects.recipes.approvable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a request is rejected.
 */
final class RequestRejected implements ApprovalEvent {
    private final String requestId;
    private final String rejecterId;
    private final String reason;
    private final Instant occurredAt;

    public RequestRejected(String requestId, String rejecterId, String reason, Instant occurredAt) {
        this.requestId = Objects.requireNonNull(requestId);
        this.rejecterId = Objects.requireNonNull(rejecterId);
        this.reason = Objects.requireNonNull(reason);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String requestId() { return requestId; }

    public String rejecterId() { return rejecterId; }

    public String reason() { return reason; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
