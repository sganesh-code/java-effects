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
public final class ApprovalRecord<ID, A, C> {
    private final ID requestId;
    private final String initiatorId;
    private Status status;
    private A requiredAuthority;
    private final List<ApprovalDecision<A, C>> history = new ArrayList<>();

    public ApprovalRecord(ID requestId, String initiatorId, Status status, A requiredAuthority) {
        this.requestId = Objects.requireNonNull(requestId);
        this.initiatorId = Objects.requireNonNull(initiatorId);
        this.status = Objects.requireNonNull(status);
        this.requiredAuthority = requiredAuthority;
    }

    public synchronized ID requestId() { return requestId; }
    public synchronized String initiatorId() { return initiatorId; }
    public synchronized Status status() { return status; }
    public synchronized A requiredAuthority() { return requiredAuthority; }
    public synchronized List<ApprovalDecision<A, C>> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }

    public synchronized boolean isTerminal() {
        return status == Status.APPROVED || status == Status.REJECTED;
    }

    public synchronized boolean hasDecisionByRole(A role, DecisionType type) {
        return history.stream().anyMatch(d -> d.actorRole().equals(role) && d.type() == type);
    }

    /**
     * Records a decision and transitions the state of the register internally.
     */
    private synchronized void recordDecision(ApprovalDecision<A, C> decision, Status nextStatus, A nextRequiredAuthority) {
        Objects.requireNonNull(decision);
        Objects.requireNonNull(nextStatus);

        if (isTerminal()) {
            throw new IllegalStateException("Cannot register a decision on a terminal approval request: " + requestId);
        }

        history.add(decision);
        status = nextStatus;
        requiredAuthority = nextRequiredAuthority;
    }

    /**
     * Behavioral Factory: Evaluates initial submission, builds the register, and produces the initial event.
     */
    public static <ID, A, C> Either<String, TransitionResult<ApprovalRecord<ID, A, C>, ApprovalEvent<ID, A>>> submit(
        ID requestId, 
        String initiatorId, 
        C submitComment,
        ApprovableRequest<ID, A, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(initiatorId);
        Objects.requireNonNull(submitComment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        InitialAssessment<A> assessment = request.evaluateInitialSubmission(now);
        ApprovalRecord<ID, A, C> record = new ApprovalRecord<>(requestId, initiatorId, Status.PENDING, null);

        ApprovalDecision<A, C> submitDecision = new ApprovalDecision<>(
            UUID.randomUUID().toString(),
            initiatorId,
            null, // INITIATOR role
            DecisionType.APPROVE,
            submitComment,
            now
        );
        record.recordDecision(submitDecision, assessment.initialStatus(), assessment.requiredAuthority());

        ApprovalEvent<ID, A> event;
        if (record.status() == Status.APPROVED) {
            event = new RequestApproved<>(requestId, initiatorId, "AUTO_APPROVED", now);
        } else {
            event = new RequestSubmitted<>(requestId, initiatorId, record.requiredAuthority(), now);
        }

        return Either.right(new TransitionResult<>(record, event));
    }

    /**
     * Behavioral Transition: Evaluates approval against domain constraints and transitions state.
     */
    public synchronized Either<String, ApprovalEvent<ID, A>> approve(
        String approverId, 
        A approverRole, 
        C detail, 
        ApprovableRequest<ID, A, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(approverId);
        Objects.requireNonNull(approverRole);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status == Status.APPROVED) {
            return Either.right(null); // Idempotent success
        }
        if (status == Status.REJECTED) {
            return Either.left("Cannot approve a rejected request: " + requestId);
        }

        // Delegate validation to pure behavioral object (double dispatch)
        Either<String, NextStep<A>> eitherNext = request.evaluateDecision(
            this, approverId, approverRole, DecisionType.APPROVE, detail, now
        );

        if (eitherNext.isLeft()) {
            return Either.left(eitherNext.getLeft());
        }

        NextStep<A> next = eitherNext.getRight();
        ApprovalDecision<A, C> decision = new ApprovalDecision<>(
            UUID.randomUUID().toString(),
            approverId,
            approverRole,
            DecisionType.APPROVE,
            detail,
            now
        );
        recordDecision(decision, next.nextStatus(), next.nextRequiredAuthority());

        ApprovalEvent<ID, A> event;
        if (status == Status.APPROVED) {
            event = new RequestApproved<>(requestId, approverId, detail.toString(), now);
        } else {
            event = new RequestSubmitted<>(requestId, initiatorId, requiredAuthority, now);
        }

        return Either.right(event);
    }

    /**
     * Behavioral Transition: Evaluates rejection against domain constraints and transitions state.
     */
    public synchronized Either<String, ApprovalEvent<ID, A>> reject(
        String approverId, 
        A approverRole, 
        C reason, 
        ApprovableRequest<ID, A, C> request, 
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
        Either<String, NextStep<A>> eitherNext = request.evaluateDecision(
            this, approverId, approverRole, DecisionType.REJECT, reason, now
        );

        if (eitherNext.isLeft()) {
            return Either.left(eitherNext.getLeft());
        }

        ApprovalDecision<A, C> decision = new ApprovalDecision<>(
            UUID.randomUUID().toString(),
            approverId,
            approverRole,
            DecisionType.REJECT,
            reason,
            now
        );
        recordDecision(decision, Status.REJECTED, null);

        ApprovalEvent<ID, A> event = new RequestRejected<>(requestId, approverId, reason.toString(), now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Evaluates escalation against domain constraints and transitions state.
     */
    public synchronized Either<String, ApprovalEvent<ID, A>> escalate(
        String approverId, 
        A approverRole, 
        A targetAuthority, 
        C reason, 
        ApprovableRequest<ID, A, C> request, 
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
        Either<String, NextStep<A>> eitherNext = request.evaluateDecision(
            this, approverId, approverRole, DecisionType.ESCALATE, reason, now
        );

        if (eitherNext.isLeft()) {
            return Either.left(eitherNext.getLeft());
        }

        ApprovalDecision<A, C> decision = new ApprovalDecision<>(
            UUID.randomUUID().toString(),
            approverId,
            approverRole,
            DecisionType.ESCALATE,
            reason,
            now
        );
        recordDecision(decision, Status.ESCALATED, targetAuthority);

        ApprovalEvent<ID, A> event = new RequestEscalated<>(requestId, approverId, targetAuthority, reason.toString(), now);
        return Either.right(event);
    }
}