package io.effects.recipes.schedulable;

import io.effects.Either;
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing the current schedule status,
 * target trigger time, and chronological history of adjustments.
 * It is an Aggregate Root that completely owns schedule state transitions and produces SchedulableEvent occurrences.
 */
public final class ScheduleLedger {
    public enum Status { INITIAL, SCHEDULED, FIRED, CANCELLED }

    private final String occurrenceId;
    private Status status = Status.INITIAL;
    private Instant triggerTime;
    private final List<ScheduleStep> history = new ArrayList<>();

    public ScheduleLedger(String occurrenceId) {
        this.occurrenceId = Objects.requireNonNull(occurrenceId);
    }

    public synchronized String occurrenceId() { return occurrenceId; }
    public synchronized Status status() { return status; }
    public synchronized Instant triggerTime() { return triggerTime; }
    public synchronized List<ScheduleStep> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }

    public synchronized boolean isTerminal() {
        return status == Status.FIRED || status == Status.CANCELLED;
    }

    /**
     * Records a step and transitions state internally.
     */
    private synchronized void recordStep(ScheduleStep step, Status nextStatus, Instant triggerTime) {
        Objects.requireNonNull(step);
        Objects.requireNonNull(nextStatus);

        if (isTerminal()) {
            throw new IllegalStateException("Cannot record a step on a terminal schedule ledger: " + occurrenceId);
        }

        this.history.add(step);
        this.status = nextStatus;
        if (triggerTime != null) {
            this.triggerTime = triggerTime;
        }
    }

    /**
     * Behavioral Factory: Evaluates, schedules, and creates the ScheduleLedger.
     */
    public static Either<String, TransitionResult<ScheduleLedger, SchedulableEvent>> schedule(
        String occurrenceId, 
        String actorId, 
        Instant triggerTime, 
        SchedulableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(occurrenceId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(triggerTime);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        ScheduleLedger ledger = new ScheduleLedger(occurrenceId);

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateSchedule(ledger, triggerTime, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        ScheduleStep step = new ScheduleStep(
            UUID.randomUUID().toString(),
            actorId,
            ScheduleStep.Type.SCHEDULE,
            triggerTime,
            "Occurrence scheduled",
            now
        );
        ledger.recordStep(step, Status.SCHEDULED, triggerTime);

        SchedulableEvent event = new OccurrenceScheduled(occurrenceId, triggerTime, now);
        return Either.right(new TransitionResult<>(ledger, event));
    }

    /**
     * Behavioral Transition: Adjusts/reschedules the occurrence trigger time.
     */
    public synchronized Either<String, SchedulableEvent> reschedule(
        String actorId, 
        Instant newTriggerTime, 
        String comment, 
        SchedulableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(newTriggerTime);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status == Status.SCHEDULED && triggerTime.equals(newTriggerTime)) {
            return Either.right(null); // Idempotent success (no-op)
        }
        if (status != Status.SCHEDULED) {
            return Either.left("Cannot reschedule: occurrence is not currently in SCHEDULED state (current status: " + status + ")");
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateReschedule(this, newTriggerTime, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        ScheduleStep step = new ScheduleStep(
            UUID.randomUUID().toString(),
            actorId,
            ScheduleStep.Type.RESCHEDULE,
            newTriggerTime,
            comment,
            now
        );
        recordStep(step, Status.SCHEDULED, newTriggerTime);

        SchedulableEvent event = new OccurrenceRescheduled(occurrenceId, newTriggerTime, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Executes/fires the scheduled task occurrence.
     */
    public synchronized Either<String, SchedulableEvent> fire(
        String actorId, 
        SchedulableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status == Status.FIRED) {
            return Either.right(null); // Idempotent success
        }
        if (status != Status.SCHEDULED) {
            return Either.left("Cannot fire: occurrence is not in SCHEDULED state (current status: " + status + ")");
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateExecution(this, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        ScheduleStep step = new ScheduleStep(
            UUID.randomUUID().toString(),
            actorId,
            ScheduleStep.Type.FIRE,
            triggerTime,
            "Occurrence fired",
            now
        );
        recordStep(step, Status.FIRED, null);

        SchedulableEvent event = new OccurrenceFired(occurrenceId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Cancels the scheduled occurrence.
     */
    public synchronized Either<String, SchedulableEvent> cancel(
        String actorId, 
        String reason, 
        SchedulableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status == Status.CANCELLED) {
            return Either.right(null); // Idempotent success
        }
        if (status != Status.SCHEDULED) {
            return Either.left("Cannot cancel: occurrence is not in SCHEDULED state (current status: " + status + ")");
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateCancellation(this, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        ScheduleStep step = new ScheduleStep(
            UUID.randomUUID().toString(),
            actorId,
            ScheduleStep.Type.CANCEL,
            triggerTime,
            reason,
            now
        );
        recordStep(step, Status.CANCELLED, null);

        SchedulableEvent event = new OccurrenceCancelled(occurrenceId, now);
        return Either.right(event);
    }
}
