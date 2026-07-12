package io.effects.recipes.retryable;

import io.effects.recipes.retryable.models.*;
import io.effects.core.Either;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing the current retry execution status
 * and history of attempts.
 * It is an Aggregate Root that completely owns state progressions and produces RetryableEvent occurrences.
 */
public final class RetryLedger<ID, C> {
    public enum Status { PENDING, ATTEMPTING, SUCCEEDED, RETRY_PENDING, FAILED }

    private final ID operationId;
    private Status status = Status.PENDING;
    private int attempts = 0;
    private String lastError = null;
    private final List<RetryStep<C>> history = new ArrayList<>();

    public RetryLedger(ID operationId) {
        this.operationId = Objects.requireNonNull(operationId);
    }

    public synchronized ID operationId() { return operationId; }
    public synchronized Status status() { return status; }
    public synchronized int attempts() { return attempts; }
    public synchronized String lastError() { return lastError; }
    public synchronized List<RetryStep<C>> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }

    public synchronized boolean isTerminal() {
        return status == Status.SUCCEEDED || status == Status.FAILED;
    }

    /**
     * Records a step and transitions state internally.
     */
    private synchronized void recordStep(RetryStep<C> step, Status nextStatus) {
        Objects.requireNonNull(step);
        Objects.requireNonNull(nextStatus);

        if (isTerminal()) {
            throw new IllegalStateException("Cannot register a retry step on a terminal retry ledger: " + operationId);
        }

        this.history.add(step);
        this.status = nextStatus;
    }

    /**
     * Behavioral Factory: Creates a new retry ledger.
     */
    public static <ID, C> RetryLedger<ID, C> initiate(ID operationId) {
        return new RetryLedger<>(operationId);
    }

    /**
     * Behavioral Transition: Records that an execution attempt is starting.
     */
    public synchronized Either<String, RetryableEvent<ID>> recordAttempt(Instant now, C comment) {
        Objects.requireNonNull(now);
        Objects.requireNonNull(comment);

        if (isTerminal()) {
            return Either.left("Cannot record attempt on a terminal retry ledger: " + status);
        }

        attempts++;

        RetryStep<C> step = new RetryStep<>(
            UUID.randomUUID().toString(),
            attempts,
            RetryStep.Type.EXECUTE_ATTEMPT,
            comment,
            now
        );
        recordStep(step, Status.ATTEMPTING);

        return Either.right(null);
    }

    /**
     * Behavioral Transition: Records that the execution succeeded.
     */
    public synchronized Either<String, RetryableEvent<ID>> recordSuccess(Instant now, C comment) {
        Objects.requireNonNull(now);
        Objects.requireNonNull(comment);

        if (status != Status.ATTEMPTING) {
            return Either.left("Must be in ATTEMPTING status to succeed, current: " + status);
        }

        RetryStep<C> step = new RetryStep<>(
            UUID.randomUUID().toString(),
            attempts,
            RetryStep.Type.SUCCESS,
            comment,
            now
        );
        recordStep(step, Status.SUCCEEDED);

        RetryableEvent<ID> event = new ExecutionSucceeded<>(operationId, attempts, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Records that the execution failed, determining if it is transient or fatal.
     */
    public synchronized Either<String, RetryableEvent<ID>> recordFailure(
        Throwable error, 
        RetryableRequest<ID, C> request, 
        C comment, 
        Instant now
    ) {
        Objects.requireNonNull(error);
        Objects.requireNonNull(request);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        if (status != Status.ATTEMPTING) {
            return Either.left("Must be in ATTEMPTING status to fail, current: " + status);
        }

        this.lastError = error.getMessage();

        boolean isTransient = request.isTransientFailure(error);
        boolean maxExceeded = attempts >= request.maxAttempts();

        if (isTransient && !maxExceeded) {
            // Transient failure, scheduled for retry
            long delay = request.calculateBackoffMillis(attempts, now);

            RetryStep<C> failureStep = new RetryStep<>(
                UUID.randomUUID().toString(),
                attempts,
                RetryStep.Type.ATTEMPT_FAILURE,
                comment,
                now
            );
            recordStep(failureStep, Status.RETRY_PENDING);

            // Record scheduling
            RetryStep<C> scheduleStep = new RetryStep<>(
                UUID.randomUUID().toString(),
                attempts,
                RetryStep.Type.SCHEDULE_RETRY,
                comment,
                now
            );
            this.history.add(scheduleStep);

            RetryableEvent<ID> event = new RetryScheduled<>(operationId, attempts + 1, delay, now);
            return Either.right(event);
        } else {
            // Fatal or max retries exceeded -> abandon
            RetryStep<C> abandonStep = new RetryStep<>(
                UUID.randomUUID().toString(),
                attempts,
                RetryStep.Type.ABANDON,
                comment,
                now
            );
            recordStep(abandonStep, Status.FAILED);

            RetryableEvent<ID> event = new ExecutionAbandoned<>(operationId, attempts, error.getMessage(), now);
            return Either.right(event);
        }
    }
}
