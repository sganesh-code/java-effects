package io.effects.recipes.throttlable;

import io.effects.recipes.throttlable.models.*;
import java.time.Instant;
import java.util.List;

/**
 * A capability-bearing visitor interface that allows controlled state inspection
 * of a TokenBucketLedger without exposing simple public getters on the ledger.
 */
public interface ThrottlableProjector<ID, C> {
    void project(
        ID actorId, 
        double currentTokens, 
        Instant lastRefillTime, 
        List<ThrottleStep<C>> history
    );
}
