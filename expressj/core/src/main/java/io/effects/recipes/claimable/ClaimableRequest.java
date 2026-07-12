package io.effects.recipes.claimable;

import io.effects.recipes.claimable.models.*;
import io.effects.core.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing a business claim or dispute.
 *
 * It contains zero passive getters/setters and executes purely and synchronously.
 */
public interface ClaimableRequest<ID, V, C> {

    /**
     * Evaluates whether a claim is allowed to be initially filed.
     */
    Either<String, Void> evaluateFile(ClaimLedger<ID, V, C> ledger, String claimantId, C comment, Instant now);

    /**
     * Evaluates whether a claim can be put under active review/assessment.
     */
    Either<String, Void> evaluateReview(ClaimLedger<ID, V, C> ledger, String reviewerId, V validatorRole, C comment, Instant now);

    /**
     * Evaluates whether a claim decision (accept or deny) is valid.
     */
    Either<String, Void> evaluateDecision(ClaimLedger<ID, V, C> ledger, String reviewerId, V validatorRole, boolean accept, C comment, Instant now);

    /**
     * Evaluates whether a terminal claim can be disputed and reopened for review.
     */
    Either<String, Void> evaluateDispute(ClaimLedger<ID, V, C> ledger, String actorId, C comment, Instant now);
}
