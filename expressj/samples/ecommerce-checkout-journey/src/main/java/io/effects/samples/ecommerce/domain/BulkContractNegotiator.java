package io.effects.samples.ecommerce.domain;

import io.effects.Either;
import io.effects.ports.EventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.negotiable.*;
import io.effects.samples.ecommerce.domain.models.BulkOrderTerms;
import java.time.Instant;

public class BulkContractNegotiator implements NegotiableRequest<String, BulkOrderTerms> {
    private final String orderId;
    private final NegotiableProcess<String, BulkOrderTerms> negotiationProcess;

    public BulkContractNegotiator(String orderId, EventPublisher<NegotiationEvent<String>> publisher) {
        this.orderId = orderId;
        this.negotiationProcess = new NegotiableProcess<>(new InMemoryStateRepository<>(), publisher, new NoOpTelemetryPort());
        this.negotiationProcess.register(orderId, this).unsafeRunSync();
    }

    public void initiate() {
        negotiationProcess.initiate(orderId).unsafeRunSync();
    }

    public void proposeTerms(String actorId, BulkOrderTerms terms, Instant time) {
        DomainLogger.info("[NEGOTIATE] Buyer submits initial terms: " + terms);
        var res = negotiationProcess.makeOffer(orderId, actorId, terms, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Offer failed: " + res.getLeft());
        }
    }

    public void counterPropose(String actorId, BulkOrderTerms terms, Instant time) {
        var res = negotiationProcess.makeCounter(orderId, actorId, terms, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Counter offer failed: " + res.getLeft());
        }
    }

    public void acceptTerms(String actorId, Instant time) {
        var res = negotiationProcess.accept(orderId, actorId, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Negotiation acceptance failed: " + res.getLeft());
        }
        DomainLogger.info("[SUCCESS] Bulk price negotiation completed and finalized!");
    }

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
