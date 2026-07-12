package io.effects.recipes.compensable;

import io.effects.recipes.compensable.models.*;
import io.effects.core.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing a distributed Saga transaction flow.
 *
 * It contains zero passive getters/setters and executes purely and synchronously.
 */
public interface CompensableRequest<ID, C> {

    /**
     * Evaluates whether a specific step is allowed to be executed in the current Saga context.
     */
    Either<String, Void> evaluateStepExecution(CompensationLedger<ID, C> ledger, String stepId, Instant now);

    /**
     * Evaluates whether compensation rollback is allowed for the Saga transaction.
     */
    Either<String, Void> evaluateRollbackTrigger(CompensationLedger<ID, C> ledger, String failedStepId, C reason, Instant now);
}
