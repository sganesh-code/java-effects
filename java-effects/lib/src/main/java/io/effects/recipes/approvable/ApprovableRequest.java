package io.effects.recipes.approvable;

import io.effects.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing a business request that requires approval.
 * 
 * In this design, the consumer's implementation is completely synchronous and pure!
 * It contains NO monadic references (no IO) or threading knowledge.
 * The monadic shell (ApprovalProcess) is responsible for lifting these pure synchronous
 * evaluations safely into the lazy, concurrent IO context.
 */
public interface ApprovableRequest {

    /**
     * Unique identifier of the request.
     */
    String requestId();

    /**
     * Who initiated this request.
     */
    String initiatorId();

    /**
     * Determines the initial approval status and required authority for this request.
     * Some requests might be auto-approved, while others require a specific level of authority.
     */
    InitialAssessment evaluateInitialSubmission(Instant now);

    /**
     * Evaluates a decision step (approve, reject, or escalate).
     * Returns the next status and next required authority on the Right, or a validation/rejection error message on the Left.
     */
    Either<String, NextStep> evaluateDecision(
        ApprovalRecord approvalRecord,
        String approverId, 
        String approverRole, 
        DecisionType decisionType, 
        String comment, 
        Instant now
    );
}
