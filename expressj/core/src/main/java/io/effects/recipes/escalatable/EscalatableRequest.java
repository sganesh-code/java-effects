package io.effects.recipes.escalatable;

import io.effects.recipes.escalatable.models.*;
import io.effects.core.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing an SLA-sensitive work item or case.
 *
 * It contains zero passive getters/setters and executes purely and synchronously.
 */
public interface EscalatableRequest<ID, T, C> {

    /**
     * Evaluates if the case can be initially filed at a proposed tier with a handler.
     */
    Either<String, Void> evaluateFile(EscalationLedger<ID, T, C> ledger, T proposedTier, String handlerId, Instant now);

    /**
     * Evaluates if an SLA warning threshold is exceeded given a deadline.
     */
    Either<String, Void> evaluateSLAWarning(EscalationLedger<ID, T, C> ledger, Instant deadline, Instant now);

    /**
     * Evaluates whether the case can be escalated from the current tier to a proposed tier.
     */
    Either<String, Void> evaluateEscalation(EscalationLedger<ID, T, C> ledger, T currentTier, T proposedTier, Instant now);

    /**
     * Evaluates whether the case can be de-escalated to a proposed lower tier.
     */
    Either<String, Void> evaluateDeescalation(EscalationLedger<ID, T, C> ledger, T currentTier, T proposedTier, Instant now);

    /**
     * Evaluates whether the case can be reassigned to a proposed new handler.
     */
    Either<String, Void> evaluateReassignment(EscalationLedger<ID, T, C> ledger, String currentHandlerId, String proposedHandlerId, Instant now);
}
