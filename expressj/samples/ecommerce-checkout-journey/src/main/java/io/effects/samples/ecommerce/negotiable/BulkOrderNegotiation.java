package io.effects.samples.ecommerce.negotiable;

import io.effects.Either;
import io.effects.recipes.negotiable.NegotiableRequest;
import io.effects.recipes.negotiable.NegotiationLedger;
import java.time.Instant;

public class BulkOrderNegotiation implements NegotiableRequest<String, BulkOrderTerms> {

    @Override
    public Either<String, Void> evaluateOffer(NegotiationLedger<String, BulkOrderTerms> ledger, String actorId, BulkOrderTerms proposal, Instant now) {
        if (proposal.quantity() < 10) {
            return Either.left("Bulk negotiation is only available for quantities of 10 or more items.");
        }
        if (proposal.unitPrice() <= 0) {
            return Either.left("Proposed unit price must be positive.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateCounter(NegotiationLedger<String, BulkOrderTerms> ledger, String actorId, BulkOrderTerms proposal, Instant now) {
        if (proposal.discountPercentage() > 50.0) {
            return Either.left("Maximum discount allowed under policy is 50.0%.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateAcceptance(NegotiationLedger<String, BulkOrderTerms> ledger, String actorId, Instant now) {
        return Either.right(null);
    }
}
