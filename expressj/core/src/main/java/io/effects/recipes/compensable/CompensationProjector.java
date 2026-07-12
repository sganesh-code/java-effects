package io.effects.recipes.compensable;

import io.effects.recipes.compensable.models.*;
import java.time.Instant;
import java.util.List;

/**
 * A capability-bearing visitor interface that allows controlled state inspection
 * of a CompensationLedger without exposing simple public getters on the ledger.
 */
public interface CompensationProjector<ID, C> {
    void project(
        ID transactionId, 
        CompensationLedger.Status status, 
        List<String> completedSteps, 
        List<SagaStep<C>> history
    );
}
