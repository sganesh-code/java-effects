package io.effects.recipes.compensable;

import io.effects.recipes.compensable.models.*;
import io.effects.core.Either;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing the current execution and rollback status
 * of a multi-step distributed Saga transaction.
 *
 * Adhering strictly to pure OOP principles:
 * - It contains **zero passive getters**.
 * - It exposes its internal state strictly via a visitor projector pattern.
 * - It owns state transitions and enforces business laws.
 */
public final class CompensationLedger<ID, C> {
    public enum Status { INITIAL, PROCESSING, COMPENSATING, COMPENSATED, FAILED, COMPLETED }

    private final ID transactionId;
    private Status status = Status.INITIAL;
    private final List<String> completedSteps = new ArrayList<>();
    private final List<SagaStep<C>> history = new ArrayList<>();

    public CompensationLedger(ID transactionId) {
        this.transactionId = Objects.requireNonNull(transactionId);
    }

    /**
     * Pure OOP State Projection: Projects state strictly onto a visitor projector.
     */
    public synchronized void projectState(CompensationProjector<ID, C> projector) {
        Objects.requireNonNull(projector);
        projector.project(
            transactionId,
            status,
            Collections.unmodifiableList(new ArrayList<>(completedSteps)),
            Collections.unmodifiableList(new ArrayList<>(history))
        );
    }

    public synchronized boolean isTerminal() {
        return status == Status.COMPENSATED || status == Status.FAILED || status == Status.COMPLETED;
    }

    public synchronized boolean isCompensating() {
        return status == Status.COMPENSATING;
    }

    public synchronized boolean hasStepSucceeded(String stepId) {
        Objects.requireNonNull(stepId);
        return completedSteps.contains(stepId);
    }

    private synchronized void recordStep(SagaStep<C> step, Status nextStatus) {
        Objects.requireNonNull(step);
        Objects.requireNonNull(nextStatus);

        this.history.add(step);
        this.status = nextStatus;
    }

    /**
     * Behavioral Factory: Creates a new compensation ledger.
     */
    public static <ID, C> CompensationLedger<ID, C> initiate(ID transactionId) {
        return new CompensationLedger<>(transactionId);
    }

    /**
     * Behavioral Transition: Marks a specific step as executed successfully.
     */
    public synchronized Either<String, CompensableEvent<ID>> markStepSuccess(
        String stepId, 
        C comment, 
        CompensableRequest<ID, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (isTerminal()) {
            return Either.left("Cannot mark step success on terminal Saga: " + status);
        }
        if (status == Status.COMPENSATING) {
            return Either.left("Cannot mark step success while Saga is in compensation rollback mode");
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateStepExecution(this, stepId, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        completedSteps.add(stepId);

        SagaStep<C> step = new SagaStep<>(
            UUID.randomUUID().toString(),
            SagaStep.Type.STEP_SUCCESS,
            comment,
            now
        );
        recordStep(step, Status.PROCESSING);

        CompensableEvent<ID> event = new SagaStepSucceeded<>(transactionId, stepId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Triggers the rollback/compensation mode for the Saga.
     */
    public synchronized Either<String, CompensableEvent<ID>> triggerRollback(
        String failedStepId, 
        C reason, 
        CompensableRequest<ID, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(failedStepId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (isTerminal()) {
            return Either.left("Cannot trigger rollback on a terminal Saga: " + status);
        }
        if (status == Status.COMPENSATING) {
            return Either.right(null); // Idempotent success
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateRollbackTrigger(this, failedStepId, reason, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        SagaStep<C> step = new SagaStep<>(
            UUID.randomUUID().toString(),
            SagaStep.Type.ROLLBACK_TRIGGER,
            reason,
            now
        );
        recordStep(step, Status.COMPENSATING);

        CompensableEvent<ID> event = new SagaRollbackTriggered<>(transactionId, failedStepId, reason.toString(), now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Confirms that all completed steps are successfully compensated.
     */
    public synchronized Either<String, CompensableEvent<ID>> markCompensated(C comment, Instant now) {
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        if (status != Status.COMPENSATING) {
            return Either.left("Can only mark compensated when Saga is in COMPENSATING state, currently: " + status);
        }

        SagaStep<C> step = new SagaStep<>(
            UUID.randomUUID().toString(),
            SagaStep.Type.COMPENSATION_SUCCESS,
            comment,
            now
        );
        recordStep(step, Status.COMPENSATED);

        CompensableEvent<ID> event = new SagaCompensated<>(transactionId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Records a compensating step failure.
     */
    public synchronized Either<String, CompensableEvent<ID>> markCompensationFailure(
        String stepId, 
        String error, 
        C comment, 
        Instant now
    ) {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(error);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        if (status != Status.COMPENSATING) {
            return Either.left("Can only record compensation failure when Saga is in COMPENSATING state, currently: " + status);
        }

        SagaStep<C> step = new SagaStep<>(
            UUID.randomUUID().toString(),
            SagaStep.Type.COMPENSATION_FAILURE,
            comment,
            now
        );
        recordStep(step, Status.FAILED);

        CompensableEvent<ID> event = new SagaCompensatedFailed<>(transactionId, stepId, error, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Marks the entire Saga as successfully completed.
     */
    public synchronized Either<String, CompensableEvent<ID>> markCompleted(C comment, Instant now) {
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        if (isTerminal()) {
            return Either.left("Cannot complete a terminal Saga: " + status);
        }
        if (status == Status.COMPENSATING) {
            return Either.left("Cannot complete a Saga that is undergoing compensation rollback");
        }

        SagaStep<C> step = new SagaStep<>(
            UUID.randomUUID().toString(),
            SagaStep.Type.STEP_SUCCESS,
            comment,
            now
        );
        recordStep(step, Status.COMPLETED);

        CompensableEvent<ID> event = new SagaStepSucceeded<>(transactionId, "COMPLETED_SAGA", now);
        return Either.right(event);
    }
}
