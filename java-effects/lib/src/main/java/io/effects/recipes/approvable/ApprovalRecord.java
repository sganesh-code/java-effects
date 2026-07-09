package io.effects.recipes.approvable;

import io.effects.Either;
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger and Aggregate Root representing an approval request.
 * It encapsulates its own invariants, chronological history, state-process boundaries,
 * and produces Domain Events representing the outcomes of transitions.
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
     * Records a decision and transitions the state of the record internally.
     */
    private synchronized void recordDecision(ApprovalDecision decision, Status nextStatus, String nextRequiredAuthority) {
        Objects.requireNonNull(decision);
        Objects.requireNonNull(nextStatus);

        if (isTerminal()) {
            throw new IllegalStateException("Cannot record a decision on a terminal approval request: " + requestId);
        }

        history.add(decision);
        status = nextStatus;
        requiredAuthority = nextRequiredAuthority;
    }

    /**
     * Behavioral Factory: Evaluates initial submission, builds the record, and produces the initial event.
     */
    public static Either<String, TransitionResult<ApprovalRecord, ApprovalEvent>> submit(
        String requestId, 
        String initiatorId, 
        ApprovableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(initiatorId);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        InitialAssessment assessment = request.evaluateInitialSubmission(now);
        ApprovalRecord record = new ApprovalRecord(requestId, initiatorId, Status.PENDING, null);

        ApprovalDecision submitDecision = new ApprovalDecision(
            UUID.randomUUID().toString(),
            initiatorId,
            "INITIATOR",
            DecisionType.APPROVE,
            "Submitted for approval",
            now
        );
        record.recordDecision(submitDecision, assessment.initialStatus(), assessment.requiredAuthority());

        ApprovalEvent event;
        if (record.status() == Status.APPROVED) {
            event = new RequestApproved(requestId, initiatorId, "AUTO_APPROVED", now);
        } else {
            event = new RequestSubmitted(requestId, initiatorId, record.requiredAuthority(), now);
        }

        return Either.right(new TransitionResult<>(record, event));
    }

    /**
     * Behavioral Transition: Evaluates approval against domain constraints and transitions state.
     */
    public synchronized Either<String, ApprovalEvent> approve(
        String approverId, 
        String approverRole, 
        String comment, 
        ApprovableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(approverId);
        Objects.requireNonNull(approverRole);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status == Status.APPROVED) {
            return Either.right(null); // Idempotent success (no new event)
        }
        if (status == Status.REJECTED) {
            return Either.left("Cannot approve a rejected request: " + requestId);
        }

        // Delegate validation to pure behavioral object (double dispatch)
        Either<String, NextStep> eitherNext = request.evaluateDecision(
            this, approverId, approverRole, DecisionType.APPROVE, comment, now
        );

        if (eitherNext.isLeft()) {
            return Either.left(eitherNext.getLeft());
        }

        NextStep next = eitherNext.getRight();
        ApprovalDecision decision = new ApprovalDecision(
            UUID.randomUUID().toString(),
            approverId,
            approverRole,
            DecisionType.APPROVE,
            comment,
            now
        );
        recordDecision(decision, next.nextStatus(), next.nextRequiredAuthority());

        ApprovalEvent event;
        if (status == Status.APPROVED) {
            event = new RequestApproved(requestId, approverId, comment, now);
        } else {
            event = new RequestSubmitted(requestId, initiatorId, requiredAuthority, now);
        }

        return Either.right(event);
    }

    /**
     * Behavioral Transition: Evaluates rejection against domain constraints and transitions state.
     */
    public synchronized Either<String, ApprovalEvent> reject(
        String approverId, 
        String approverRole, 
        String reason, 
        ApprovableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(approverId);
        Objects.requireNonNull(approverRole);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status == Status.REJECTED) {
            return Either.right(null); // Idempotent success
        }
        if (status == Status.APPROVED) {
            return Either.left("Cannot reject an already approved request: " + requestId);
        }

        // Delegate validation to pure behavioral object
        Either<String, NextStep> eitherNext = request.evaluateDecision(
            this, approverId, approverRole, DecisionType.REJECT, reason, now
        );

        if (eitherNext.isLeft()) {
            return Either.left(eitherNext.getLeft());
        }

        ApprovalDecision decision = new ApprovalDecision(
            UUID.randomUUID().toString(),
            approverId,
            approverRole,
            DecisionType.REJECT,
            reason,
            now
        );
        recordDecision(decision, Status.REJECTED, null);

        ApprovalEvent event = new RequestRejected(requestId, approverId, reason, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Evaluates escalation against domain constraints and transitions state.
     */
    public synchronized Either<String, ApprovalEvent> escalate(
        String approverId, 
        String approverRole, 
        String targetAuthority, 
        String reason, 
        ApprovableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(approverId);
        Objects.requireNonNull(approverRole);
        Objects.requireNonNull(targetAuthority);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (isTerminal()) {
            return Either.left("Cannot escalate a finalized request: " + requestId);
        }

        // Delegate validation to pure behavioral object
        Either<String, NextStep> eitherNext = request.evaluateDecision(
            this, approverId, approverRole, DecisionType.ESCALATE, reason, now
        );

        if (eitherNext.isLeft()) {
            return Either.left(eitherNext.getLeft());
        }

        ApprovalDecision decision = new ApprovalDecision(
            UUID.randomUUID().toString(),
            approverId,
            approverRole,
            DecisionType.ESCALATE,
            reason,
            now
        );
        recordDecision(decision, Status.ESCALATED, targetAuthority);

        ApprovalEvent event = new RequestEscalated(requestId, approverId, targetAuthority, reason, now);
        return Either.right(event);
    }
}
