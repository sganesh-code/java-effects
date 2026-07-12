package io.effects.recipes.reconciliable;

import io.effects.recipes.reconciliable.models.*;
import io.effects.core.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing a set of reconciliation criteria/records.
 *
 * It contains zero passive getters/setters and executes purely and synchronously.
 */
public interface ReconciliableRequest<ID, K, E, C> {

    /**
     * Evaluates if the external item can be matched with our internal record.
     */
    Either<String, Void> evaluateMatching(ReconciliationLedger<ID, K, E, C> ledger, K itemId, E externalItem, Instant now);

    /**
     * Evaluates if a discrepancy should be flagged for the item.
     */
    Either<String, Void> evaluateDiscrepancy(ReconciliationLedger<ID, K, E, C> ledger, K itemId, String discrepancyCode, Instant now);

    /**
     * Evaluates if a previously flagged discrepancy can be resolved.
     */
    Either<String, Void> evaluateResolution(ReconciliationLedger<ID, K, E, C> ledger, K itemId, String resolutionType, C comment, Instant now);
}
