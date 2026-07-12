package io.effects.recipes.auditable;

import io.effects.recipes.auditable.models.*;

import io.effects.core.Either;
import java.time.Instant;
import java.util.List;

/**
 * A purely behavioral, non-anemic object representing an asset or context to be audited.
 * It contains NO passive getters, relying entirely on double-dispatch / object collaboration.
 */
public interface AuditableRequest<ID, E, S> {

    /**
     * Behavioral Message: Evaluates whether a proposed audit entry is allowed.
     */
    Either<String, Void> evaluateEntry(AuditLedger<ID, E> ledger, E detail, Instant now);

    /**
     * Behavioral Message: Reconstructs the aggregate's semantic business state `S` 
     * by sequentially replaying the chronological history.
     */
    S reconstructState(List<AuditStep<E>> history);

    /**
     * Behavioral Message: Explains specific historical decisions or states.
     */
    String explainDecision(List<AuditStep<E>> history, String decisionStepId);
}