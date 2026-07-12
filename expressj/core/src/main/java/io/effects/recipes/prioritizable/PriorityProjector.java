package io.effects.recipes.prioritizable;

import io.effects.recipes.prioritizable.models.*;
import java.time.Instant;
import java.util.List;

/**
 * A capability-bearing visitor interface that allows controlled state inspection
 * of a PriorityLedger without exposing simple public getters on the ledger.
 */
public interface PriorityProjector<ID, P, C> {
    void project(
        ID workId, 
        PriorityLedger.Status status, 
        P currentPriority, 
        Instant deferredUntil, 
        List<PriorityStep<P, C>> history
    );
}
