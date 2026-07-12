package io.effects.recipes.prioritizable;

import io.effects.recipes.prioritizable.models.*;
import io.effects.core.Either;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing the current priority status
 * and history of sequencing decisions.
 *
 * Adhering strictly to pure OOP principles:
 * - It contains **zero passive getters**.
 * - It exposes its internal state strictly via a visitor projector pattern.
 * - It owns state transitions and enforces business laws.
 */
public final class PriorityLedger<ID, P, C> {
    public enum Status { UNTRIAGED, SEQUENCED, REPRIORITIZED, DEFERRED, EXPEDITED }

    private final ID workId;
    private Status status = Status.UNTRIAGED;
    private P currentPriority = null;
    private Instant deferredUntil = null;
    private final List<PriorityStep<P, C>> history = new ArrayList<>();

    public PriorityLedger(ID workId) {
        this.workId = Objects.requireNonNull(workId);
    }

    /**
     * Pure OOP State Projection: Projects state strictly onto a visitor projector.
     * Prevents internal state leakages while retaining full testability and serialization capability.
     */
    public synchronized void projectState(PriorityProjector<ID, P, C> projector) {
        Objects.requireNonNull(projector);
        projector.project(
            workId,
            status,
            currentPriority,
            deferredUntil,
            Collections.unmodifiableList(new ArrayList<>(history))
        );
    }

    /**
     * Behavioral Query: Check if the work item has already been prioritized.
     */
    public synchronized boolean hasPriorityBeenSet() {
        return status != Status.UNTRIAGED;
    }

    /**
     * Behavioral Query: Check if the work priority is currently deferred.
     */
    public synchronized boolean isCurrentlyDeferred(Instant now) {
        Objects.requireNonNull(now);
        return status == Status.DEFERRED && deferredUntil != null && now.isBefore(deferredUntil);
    }

    /**
     * Behavioral Query: Check if the work priority has been expedited.
     */
    public synchronized boolean isExpedited() {
        return status == Status.EXPEDITED;
    }

    private synchronized void recordStep(PriorityStep<P, C> step, Status nextStatus, P nextPriority, Instant nextDeferredUntil) {
        Objects.requireNonNull(step);
        Objects.requireNonNull(nextStatus);

        this.history.add(step);
        this.status = nextStatus;
        this.currentPriority = nextPriority;
        this.deferredUntil = nextDeferredUntil;
    }

    /**
     * Behavioral Factory: Creates a new priority ledger.
     */
    public static <ID, P, C> PriorityLedger<ID, P, C> initiate(ID workId) {
        return new PriorityLedger<>(workId);
    }

    /**
     * Behavioral Transition: Sequences work priority for the first time.
     */
    public synchronized Either<String, PrioritizableEvent<ID, P>> sequence(
        P proposedPriority, 
        C comment, 
        PrioritizableRequest<ID, P, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(proposedPriority);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (hasPriorityBeenSet()) {
            return Either.left("Priority has already been sequenced on work: " + workId);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateInitialPriority(this, proposedPriority, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        PriorityStep<P, C> step = new PriorityStep<>(
            UUID.randomUUID().toString(),
            proposedPriority,
            PriorityStep.Type.SEQUENCE,
            comment,
            now
        );
        recordStep(step, Status.SEQUENCED, proposedPriority, null);

        PrioritizableEvent<ID, P> event = new WorkSequenced<>(workId, proposedPriority, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Adjusts the priority level (reprioritization).
     */
    public synchronized Either<String, PrioritizableEvent<ID, P>> reprioritize(
        P proposedPriority, 
        C comment, 
        PrioritizableRequest<ID, P, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(proposedPriority);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (!hasPriorityBeenSet()) {
            return Either.left("Cannot reprioritize untriaged work: " + workId);
        }

        if (Objects.equals(currentPriority, proposedPriority)) {
            return Either.left("Proposed priority is already the current priority level: " + proposedPriority);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateReprioritization(this, currentPriority, proposedPriority, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        P previousPriority = this.currentPriority;

        PriorityStep<P, C> step = new PriorityStep<>(
            UUID.randomUUID().toString(),
            proposedPriority,
            PriorityStep.Type.REPRIORITIZE,
            comment,
            now
        );
        recordStep(step, Status.REPRIORITIZED, proposedPriority, null);

        PrioritizableEvent<ID, P> event = new WorkReprioritized<>(workId, previousPriority, proposedPriority, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Defers priority execution until a future instant.
     */
    public synchronized Either<String, PrioritizableEvent<ID, P>> defer(
        Instant deferredUntil, 
        C comment, 
        PrioritizableRequest<ID, P, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(deferredUntil);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (!hasPriorityBeenSet()) {
            return Either.left("Cannot defer untriaged work: " + workId);
        }

        if (deferredUntil.isBefore(now)) {
            return Either.left("Cannot defer until a past timestamp: " + deferredUntil);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateDeferral(this, deferredUntil, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        PriorityStep<P, C> step = new PriorityStep<>(
            UUID.randomUUID().toString(),
            currentPriority,
            PriorityStep.Type.DEFER,
            comment,
            now
        );
        recordStep(step, Status.DEFERRED, currentPriority, deferredUntil);

        PrioritizableEvent<ID, P> event = new WorkDeferred<>(workId, deferredUntil, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Expedites priority execution.
     */
    public synchronized Either<String, PrioritizableEvent<ID, P>> expedite(
        C comment, 
        PrioritizableRequest<ID, P, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (!hasPriorityBeenSet()) {
            return Either.left("Cannot expedite untriaged work: " + workId);
        }

        if (status == Status.EXPEDITED) {
            return Either.right(null); // Idempotent success
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateExpedition(this, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        PriorityStep<P, C> step = new PriorityStep<>(
            UUID.randomUUID().toString(),
            currentPriority,
            PriorityStep.Type.EXPEDITE,
            comment,
            now
        );
        recordStep(step, Status.EXPEDITED, currentPriority, null);

        PrioritizableEvent<ID, P> event = new WorkExpedited<>(workId, now);
        return Either.right(event);
    }
}
