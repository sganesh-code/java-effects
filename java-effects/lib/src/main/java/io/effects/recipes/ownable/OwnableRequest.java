package io.effects.recipes.ownable;

import io.effects.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing an asset or entity that can be owned.
 * 
 * In this design, the consumer's implementation is completely synchronous and pure!
 * It contains NO monadic references (no IO) or threading knowledge.
 * The monadic shell (OwnableProcess) is responsible for lifting these pure synchronous
 * evaluations safely into the lazy, concurrent IO context.
 */
public interface OwnableRequest {

    /**
     * Behavioral Message: Evaluates whether an initial owner assignment is allowed under current rules.
     */
    Either<String, Void> evaluateInitialAssignment(String ownerId, Instant now);

    /**
     * Behavioral Message: Evaluates a proposed transfer or revocation.
     * Receives the ownership record (state ledger) and current action context to decide
     * whether the transition is valid.
     */
    Either<String, Void> evaluateTransfer(
        OwnershipRecord record,
        String currentOwnerId,
        String proposedOwnerId,
        String actorId,
        Instant now
    );
}
