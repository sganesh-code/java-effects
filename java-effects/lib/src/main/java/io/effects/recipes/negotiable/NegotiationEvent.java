package io.effects.recipes.negotiable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the negotiable recipe.
 */
public interface NegotiationEvent<ID> {

    /**
     * Unique identifier of the negotiation session.
     */
    ID sessionId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();

    /**
     * Event published when an initial offer is successfully made.
     */
    record OfferMade<ID, P>(ID sessionId, String actorId, P proposal, Instant occurredAt) implements NegotiationEvent<ID> {}

    /**
     * Event published when a counter-offer is successfully made.
     */
    record CounterOfferMade<ID, P>(ID sessionId, String actorId, P proposal, Instant occurredAt) implements NegotiationEvent<ID> {}

    /**
     * Event published when a negotiation session is agreed and finalized.
     */
    record NegotiationAccepted<ID>(ID sessionId, String actorId, Instant occurredAt) implements NegotiationEvent<ID> {}

    /**
     * Event published when a party withdraws from active negotiation.
     */
    record NegotiationWithdrawn<ID>(ID sessionId, String actorId, Instant occurredAt) implements NegotiationEvent<ID> {}
}