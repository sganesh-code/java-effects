package io.effects.recipes.escalatable;

import io.effects.recipes.escalatable.models.*;
import java.util.List;

/**
 * A capability-bearing visitor interface that allows controlled state inspection
 * of an EscalationLedger without exposing simple public getters on the ledger.
 */
public interface EscalationProjector<ID, T, C> {
    void project(
        ID caseId, 
        EscalationLedger.Status status, 
        T currentTier, 
        String currentHandlerId, 
        List<EscalationStep<T, C>> history
    );
}
