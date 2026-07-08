package io.effects.recipes.approvable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when a request is escalated to a higher required authority.
 */
public final class RequestEscalated implements ApprovalEvent {
    private final String requestId;
    private final String escalatorId;
    private final String targetAuthority;
    private final String reason;
    private final Instant occurredAt;

    public RequestEscalated(String requestId, String escalatorId, String targetAuthority, String reason, Instant occurredAt) {
        this.requestId = Objects.requireNonNull(requestId);
        this.escalatorId = Objects.requireNonNull(escalatorId);
        this.targetAuthority = Objects.requireNonNull(targetAuthority);
        this.reason = Objects.requireNonNull(reason);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String requestId() { return requestId; }

    public String escalatorId() { return escalatorId; }

    public String targetAuthority() { return targetAuthority; }

    public String reason() { return reason; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
