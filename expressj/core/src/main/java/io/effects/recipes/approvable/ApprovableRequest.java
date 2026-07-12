package io.effects.recipes.approvable;

import io.effects.recipes.approvable.models.*;

import io.effects.core.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing a business request that requires approval.
 * 
 * In this design, the consumer's implementation is completely synchronous and pure!
 * It contains NO monadic references (no IO) or threading knowledge.
 * The monadic shell (ApprovalProcess) is responsible for lifting these pure synchronous
 * evaluations safely into the lazy, concurrent IO context.
 */
public interface ApprovableRequest<ID, A, C> {

    /**
     * Behavioral Message: Evaluates whether the initial submission is allowed, and returns
     * the initial status and required authority.
     */
    InitialAssessment<A> evaluateInitialSubmission(Instant now);

    /**
     * Behavioral Message: Evaluates a decision (e.g. approve, reject, escalate) against current rules.
     * Receives the approval register (state ledger) and current action context to decide
     * the next status and next required authority.
     */
    Either<String, NextStep<A>> evaluateDecision(
        ApprovalRecord<ID, A, C> record, 
        String approverId, 
        A approverRole, 
        DecisionType decisionType, 
        C detail, 
        Instant now
    );
}