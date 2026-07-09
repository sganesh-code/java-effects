package io.effects.recipes.negotiable;

import io.effects.Either;
import java.time.Instant;

/**
 * A purely behavioral, non-anemic object representing negotiation terms.
 * It contains NO passive getters, relying entirely on double-dispatch / object collaboration.
 */
public interface NegotiableRequest<ID, P> {

    /**
     * Behavioral Message: Evaluates whether a proposed initial offer is valid.
     */
    Either<String, Void> evaluateOffer(NegotiationLedger<ID, P> ledger, String actorId, P proposal, Instant now);

    /**
     * Behavioral Message: Evaluates whether a proposed counter-offer is valid.
     */
    Either<String, Void> evaluateCounter(NegotiationLedger<ID, P> ledger, String actorId, P proposal, Instant now);

    /**
     * Behavioral Message: Evaluates whether accepting the current active offer is valid.
     */
    Either<String, Void> evaluateAcceptance(NegotiationLedger<ID, P> ledger, String actorId, Instant now);
}