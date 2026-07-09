package io.effects.recipes.schedulable;

import io.effects.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing a scheduled timed occurrence.
 * 
 * In this design, the consumer's implementation is completely synchronous and pure!
 * It contains NO monadic references (no IO) or threading knowledge.
 * The monadic shell (SchedulableProcess) is responsible for lifting these pure synchronous
 * evaluations safely into the lazy, concurrent IO context.
 */
public interface SchedulableRequest<ID, T> {

    /**
     * Behavioral Message: Evaluates whether an initial schedule is allowed.
     */
    Either<String, Void> evaluateSchedule(ScheduleLedger<ID, T> ledger, T triggerTime, Instant now);

    /**
     * Behavioral Message: Evaluates whether rescheduling/adjusting the trigger time is allowed.
     */
    Either<String, Void> evaluateReschedule(ScheduleLedger<ID, T> ledger, T newTriggerTime, Instant now);

    /**
     * Behavioral Message: Evaluates whether firing/executing the task is allowed.
     */
    Either<String, Void> evaluateExecution(ScheduleLedger<ID, T> ledger, Instant now);

    /**
     * Behavioral Message: Evaluates whether cancelling the scheduled task is allowed.
     */
    Either<String, Void> evaluateCancellation(ScheduleLedger<ID, T> ledger, Instant now);
}