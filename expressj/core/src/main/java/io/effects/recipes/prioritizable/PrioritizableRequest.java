package io.effects.recipes.prioritizable;

import io.effects.recipes.prioritizable.models.*;
import io.effects.core.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing a work item that can be prioritized.
 *
 * It contains zero passive getters/setters and executes purely and synchronously.
 */
public interface PrioritizableRequest<ID, P, C> {

    /**
     * Evaluates whether initial sequencing with the proposed priority is valid.
     */
    Either<String, Void> evaluateInitialPriority(PriorityLedger<ID, P, C> ledger, P proposedPriority, Instant now);

    /**
     * Evaluates whether reprioritizing from current priority to a new proposed priority is valid.
     */
    Either<String, Void> evaluateReprioritization(PriorityLedger<ID, P, C> ledger, P currentPriority, P proposedPriority, Instant now);

    /**
     * Evaluates whether deferring the priority execution is valid.
     */
    Either<String, Void> evaluateDeferral(PriorityLedger<ID, P, C> ledger, Instant deferredUntil, Instant now);

    /**
     * Evaluates whether expediting/escalating priority is valid.
     */
    Either<String, Void> evaluateExpedition(PriorityLedger<ID, P, C> ledger, Instant now);
}
