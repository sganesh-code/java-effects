package io.effects.recipes.escalatable;

import io.effects.recipes.escalatable.models.*;
import io.effects.core.Either;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing the current escalation tier, reassignment,
 * and SLA warning status.
 *
 * Adhering strictly to pure OOP principles:
 * - It contains **zero passive getters**.
 * - It exposes its internal state strictly via a visitor projector pattern.
 * - It owns state transitions and enforces business laws.
 */
public final class EscalationLedger<ID, T, C> {
    public enum Status { STANDARD, SLA_WARNING, ESCALATED, REASSIGNED, RESOLVED }

    private final ID caseId;
    private Status status = null;
    private T currentTier = null;
    private String currentHandlerId = null;
    private final List<EscalationStep<T, C>> history = new ArrayList<>();

    public EscalationLedger(ID caseId) {
        this.caseId = Objects.requireNonNull(caseId);
    }

    /**
     * Pure OOP State Projection: Projects state strictly onto a visitor projector.
     */
    public synchronized void projectState(EscalationProjector<ID, T, C> projector) {
        Objects.requireNonNull(projector);
        projector.project(
            caseId,
            status,
            currentTier,
            currentHandlerId,
            Collections.unmodifiableList(new ArrayList<>(history))
        );
    }

    public synchronized boolean isTerminal() {
        return status == Status.RESOLVED;
    }

    public synchronized boolean isFiled() {
        return status != null;
    }

    private synchronized void recordStep(EscalationStep<T, C> step, Status nextStatus, T nextTier, String nextHandlerId) {
        Objects.requireNonNull(step);
        Objects.requireNonNull(nextStatus);

        this.history.add(step);
        this.status = nextStatus;
        this.currentTier = nextTier;
        this.currentHandlerId = nextHandlerId;
    }

    /**
     * Behavioral Factory: Creates a new escalation ledger.
     */
    public static <ID, T, C> EscalationLedger<ID, T, C> initiate(ID caseId) {
        return new EscalationLedger<>(caseId);
    }

    /**
     * Behavioral Transition: Files a new case at a starting tier.
     */
    public synchronized Either<String, EscalatableEvent<ID, T>> file(
        T proposedTier, 
        String handlerId, 
        C comment, 
        EscalatableRequest<ID, T, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(proposedTier);
        Objects.requireNonNull(handlerId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (isFiled()) {
            return Either.left("Case has already been filed: " + caseId);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateFile(this, proposedTier, handlerId, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        EscalationStep<T, C> step = new EscalationStep<>(
            UUID.randomUUID().toString(),
            proposedTier,
            handlerId,
            EscalationStep.Type.INITIALIZE,
            comment,
            now
        );
        recordStep(step, Status.STANDARD, proposedTier, handlerId);

        EscalatableEvent<ID, T> event = new CaseEscalated<>(caseId, proposedTier, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Triggers an SLA Warning.
     */
    public synchronized Either<String, EscalatableEvent<ID, T>> triggerSLAWarning(
        Instant deadline, 
        C comment, 
        EscalatableRequest<ID, T, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(deadline);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (!isFiled()) {
            return Either.left("Cannot trigger SLA warning on unfiled case: " + caseId);
        }
        if (isTerminal()) {
            return Either.left("Cannot trigger SLA warning on terminal resolved case: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateSLAWarning(this, deadline, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        EscalationStep<T, C> step = new EscalationStep<>(
            UUID.randomUUID().toString(),
            currentTier,
            currentHandlerId,
            EscalationStep.Type.WARNING,
            comment,
            now
        );
        recordStep(step, Status.SLA_WARNING, currentTier, currentHandlerId);

        EscalatableEvent<ID, T> event = new SLAWarningTriggered<>(caseId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Escalates case to a higher tier.
     */
    public synchronized Either<String, EscalatableEvent<ID, T>> escalate(
        T proposedTier, 
        C comment, 
        EscalatableRequest<ID, T, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(proposedTier);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (!isFiled()) {
            return Either.left("Cannot escalate unfiled case: " + caseId);
        }
        if (isTerminal()) {
            return Either.left("Cannot escalate terminal resolved case: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateEscalation(this, currentTier, proposedTier, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        EscalationStep<T, C> step = new EscalationStep<>(
            UUID.randomUUID().toString(),
            proposedTier,
            currentHandlerId,
            EscalationStep.Type.ESCALATE,
            comment,
            now
        );
        recordStep(step, Status.ESCALATED, proposedTier, currentHandlerId);

        EscalatableEvent<ID, T> event = new CaseEscalated<>(caseId, proposedTier, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: De-escalates case to a lower tier.
     */
    public synchronized Either<String, EscalatableEvent<ID, T>> deescalate(
        T proposedTier, 
        C comment, 
        EscalatableRequest<ID, T, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(proposedTier);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (!isFiled()) {
            return Either.left("Cannot de-escalate unfiled case: " + caseId);
        }
        if (isTerminal()) {
            return Either.left("Cannot de-escalate terminal resolved case: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateDeescalation(this, currentTier, proposedTier, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        EscalationStep<T, C> step = new EscalationStep<>(
            UUID.randomUUID().toString(),
            proposedTier,
            currentHandlerId,
            EscalationStep.Type.DEESCALATE,
            comment,
            now
        );
        recordStep(step, Status.STANDARD, proposedTier, currentHandlerId);

        EscalatableEvent<ID, T> event = new CaseDeescalated<>(caseId, proposedTier, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Reassigns the case to a new handler.
     */
    public synchronized Either<String, EscalatableEvent<ID, T>> reassign(
        String proposedHandlerId, 
        C comment, 
        EscalatableRequest<ID, T, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(proposedHandlerId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (!isFiled()) {
            return Either.left("Cannot reassign unfiled case: " + caseId);
        }
        if (isTerminal()) {
            return Either.left("Cannot reassign terminal resolved case: " + status);
        }

        if (Objects.equals(currentHandlerId, proposedHandlerId)) {
            return Either.left("Proposed handler is already the current handler: " + proposedHandlerId);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateReassignment(this, currentHandlerId, proposedHandlerId, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        String previousHandler = this.currentHandlerId;

        EscalationStep<T, C> step = new EscalationStep<>(
            UUID.randomUUID().toString(),
            currentTier,
            proposedHandlerId,
            EscalationStep.Type.REASSIGN,
            comment,
            now
        );
        recordStep(step, Status.REASSIGNED, currentTier, proposedHandlerId);

        EscalatableEvent<ID, T> event = new CaseReassigned<>(caseId, previousHandler, proposedHandlerId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Resolves the case, closing the escalation.
     */
    public synchronized Either<String, EscalatableEvent<ID, T>> resolve(C comment, Instant now) {
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        if (!isFiled()) {
            return Either.left("Cannot resolve unfiled case: " + caseId);
        }
        if (isTerminal()) {
            return Either.right(null); // Idempotent success
        }

        EscalationStep<T, C> step = new EscalationStep<>(
            UUID.randomUUID().toString(),
            currentTier,
            currentHandlerId,
            EscalationStep.Type.REASSIGN, // Closing step
            comment,
            now
        );
        recordStep(step, Status.RESOLVED, currentTier, currentHandlerId);

        return Either.right(null);
    }
}
