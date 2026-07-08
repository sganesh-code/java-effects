package io.effects.recipes.approvable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A non-anemic, thread-safe (via local confinement and synchronization) domain state ledger
 * representing the current status, required authority level, and history of decisions.
 */
public final class ApprovalRecord {
    private final String requestId;
    private final String initiatorId;
    private Status status;
    private String requiredAuthority;
    private final List<ApprovalDecision> history = new ArrayList<>();

    public ApprovalRecord(String requestId, String initiatorId, Status status, String requiredAuthority) {
        this.requestId = Objects.requireNonNull(requestId);
        this.initiatorId = Objects.requireNonNull(initiatorId);
        this.status = Objects.requireNonNull(status);
        this.requiredAuthority = requiredAuthority;
    }

    public synchronized String requestId() { return requestId; }
    public synchronized String initiatorId() { return initiatorId; }
    public synchronized Status status() { return status; }
    public synchronized String requiredAuthority() { return requiredAuthority; }
    public synchronized List<ApprovalDecision> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }

    public synchronized boolean isTerminal() {
        return status == Status.APPROVED || status == Status.REJECTED;
    }

    public synchronized boolean hasDecisionByRole(String role, DecisionType type) {
        return history.stream().anyMatch(d -> d.actorRole().equalsIgnoreCase(role) && d.type() == type);
    }

    /**
     * Records a decision and transitions the state of the record.
     * Enforces that no decisions can be appended if the record has reached a terminal state.
     */
    public synchronized void recordDecision(ApprovalDecision decision, Status nextStatus, String nextRequiredAuthority) {
        Objects.requireNonNull(decision);
        Objects.requireNonNull(nextStatus);

        if (isTerminal()) {
            throw new IllegalStateException("Cannot record a decision on a terminal approval request: " + requestId);
        }

        history.add(decision);
        status = nextStatus;
        requiredAuthority = nextRequiredAuthority;
    }
}
