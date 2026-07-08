package io.effects.recipes.approvable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a request is submitted and enters the pending or escalated state.
 */
public final class RequestSubmitted implements ApprovalEvent {
    private final String requestId;
    private final String initiatorId;
    private final String requiredAuthority;
    private final Instant occurredAt;

    public RequestSubmitted(String requestId, String initiatorId, String requiredAuthority, Instant occurredAt) {
        this.requestId = Objects.requireNonNull(requestId);
        this.initiatorId = Objects.requireNonNull(initiatorId);
        this.requiredAuthority = requiredAuthority;
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String requestId() { return requestId; }

    public String initiatorId() { return initiatorId; }

    public String requiredAuthority() { return requiredAuthority; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
