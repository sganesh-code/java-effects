package io.effects.recipes.negotiable;

import io.effects.recipes.negotiable.models.*;

import io.effects.core.Either;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NegotiableRecipeTest {

    // A custom, clean, non-anemic representation of contract proposal terms P.
    private record ContractOffer(double proposedPrice, int durationDays) {}

    // A concrete, behavioral domain request representing a freelance consulting deal.
    private static final class ConsultingNegotiation implements NegotiableRequest<String, ContractOffer> {

        ConsultingNegotiation() {}

        @Override
        public Either<String, Void> evaluateOffer(NegotiationLedger<String, ContractOffer> ledger, String actorId, ContractOffer proposal, Instant now) {
            if (proposal.proposedPrice() <= 0) {
                return Either.left("Proposed contract price must be positive");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateCounter(NegotiationLedger<String, ContractOffer> ledger, String actorId, ContractOffer proposal, Instant now) {
            if (proposal.proposedPrice() <= 0) {
                return Either.left("Proposed contract price must be positive");
            }

            // Get last active offer to verify bargaining bounds (e.g. max 50% deviation allowed)
            ContractOffer lastOffer = ledger.history().get(ledger.history().size() - 1).proposal();
            double pctDiff = Math.abs(proposal.proposedPrice() - lastOffer.proposedPrice()) / lastOffer.proposedPrice();
            if (pctDiff > 0.50) {
                return Either.left("Counter proposal price deviation " + String.format("%.1f", pctDiff * 100) + "% exceeds maximum allowed deviation of 50%");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateAcceptance(NegotiationLedger<String, ContractOffer> ledger, String actorId, Instant now) {
            // Acceptance is always allowed if bounds checks passed on proposals
            return Either.right(null);
        }
    }

    // 1. Initial Offer & Turn Invariants
    @Test
    void testNegotiationOfferAndTurnTaking() {
        NegotiableProcess<String, ContractOffer> process = new NegotiableProcess<>();
        ConsultingNegotiation deal = new ConsultingNegotiation();
        process.register("session-1", deal).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T14:00:00Z");

        process.initiate("session-1").unsafeRunSync();

        // Fails because initial offer must be positive
        Either<String, NegotiationLedger<String, ContractOffer>> badOffer = process.makeOffer("session-1", "freelancer-A", new ContractOffer(-500.0, 30), t0).unsafeRunSync();
        assertTrue(badOffer.isLeft());
        assertTrue(badOffer.getLeft().contains("Proposed contract price must be positive"));

        // Freelancer A makes initial offer of $10,000 -> succeeds
        Either<String, NegotiationLedger<String, ContractOffer>> offerResult = process.makeOffer("session-1", "freelancer-A", new ContractOffer(10000.0, 30), t0).unsafeRunSync();
        assertTrue(offerResult.isRight());
        NegotiationLedger<String, ContractOffer> ledger = offerResult.getRight();

        assertEquals(NegotiationLedger.Status.PENDING, ledger.status());

        // Invariant: Freelancer A cannot counter their own offer (Turn Taking Law!)
        Either<String, NegotiationLedger<String, ContractOffer>> doubleCounter = process.makeCounter("session-1", "freelancer-A", new ContractOffer(9500.0, 30), t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(doubleCounter.isLeft());
        assertTrue(doubleCounter.getLeft().contains("awaiting response from other party"));

        // Client B makes counter-proposal of $8,000 -> succeeds
        Either<String, NegotiationLedger<String, ContractOffer>> counterResult = process.makeCounter("session-1", "client-B", new ContractOffer(8000.0, 30), t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(counterResult.isRight());
        assertEquals(2, counterResult.getRight().history().size());
    }

    // 2. Bargaining Bounds, Acceptance Finality, and Events
    @Test
    void testNegotiationBoundsAndAcceptance() {
        InMemoryStateRepository<String, NegotiationLedger<String, ContractOffer>> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<NegotiationEvent<String>> publisher = new InMemoryEventPublisher<>();
        NegotiableProcess<String, ContractOffer> process = new NegotiableProcess<>(repository, publisher, new NoOpTelemetryPort());

        ConsultingNegotiation deal = new ConsultingNegotiation();
        process.register("session-2", deal).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T15:00:00Z");

        process.initiate("session-2").unsafeRunSync();

        // Freelancer A offers $10,000
        process.makeOffer("session-2", "freelancer-A", new ContractOffer(10000.0, 30), t0).unsafeRunSync();

        // Client B counters with $4,000 -> Fails (Bargaining Bounds Law: price deviation is 60%, exceeds 50% limit!)
        Either<String, NegotiationLedger<String, ContractOffer>> badCounter = process.makeCounter("session-2", "client-B", new ContractOffer(4000.0, 30), t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(badCounter.isLeft());
        assertTrue(badCounter.getLeft().contains("exceeds maximum allowed deviation of 50%"));

        // Client B counters with $7,000 -> Succeeds
        Either<String, NegotiationLedger<String, ContractOffer>> goodCounter = process.makeCounter("session-2", "client-B", new ContractOffer(7000.0, 30), t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(goodCounter.isRight());

        // Invariant: Client B cannot accept their own counter-offer
        Either<String, NegotiationLedger<String, ContractOffer>> selfAccept = process.accept("session-2", "client-B", t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(selfAccept.isLeft());
        assertTrue(selfAccept.getLeft().contains("Cannot accept your own proposal"));

        // Freelancer A accepts the $7,000 offer -> Succeeds (transitions to AGREED)
        Either<String, NegotiationLedger<String, ContractOffer>> acceptResult = process.accept("session-2", "freelancer-A", t0.plusSeconds(40)).unsafeRunSync();
        assertTrue(acceptResult.isRight());
        NegotiationLedger<String, ContractOffer> finalizedLedger = acceptResult.getRight();

        assertEquals(NegotiationLedger.Status.AGREED, finalizedLedger.status());
        assertTrue(finalizedLedger.isTerminal());

        // Post-Agreement Finality: Further counter proposals are blocked
        Either<String, NegotiationLedger<String, ContractOffer>> postAgreementCounter = process.makeCounter("session-2", "client-B", new ContractOffer(6500.0, 30), t0.plusSeconds(50)).unsafeRunSync();
        assertTrue(postAgreementCounter.isLeft());

        // Verify Event Publication
        List<NegotiationEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(3, events.size()); // offer, counter, accept
        assertTrue(events.get(0) instanceof NegotiationEvent.OfferMade);
        assertTrue(events.get(1) instanceof NegotiationEvent.CounterOfferMade);
        assertTrue(events.get(2) instanceof NegotiationEvent.NegotiationAccepted);
    }

    // 3. Negotiation Withdrawal Flow
    @Test
    void testNegotiationWithdrawal() {
        InMemoryStateRepository<String, NegotiationLedger<String, ContractOffer>> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<NegotiationEvent<String>> publisher = new InMemoryEventPublisher<>();
        NegotiableProcess<String, ContractOffer> process = new NegotiableProcess<>(repository, publisher, new NoOpTelemetryPort());

        ConsultingNegotiation deal = new ConsultingNegotiation();
        process.register("session-3", deal).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T16:00:00Z");

        process.initiate("session-3").unsafeRunSync();

        process.makeOffer("session-3", "freelancer-A", new ContractOffer(12000.0, 45), t0).unsafeRunSync();

        // Freelancer A withdraws -> Succeeds (transitions to WITHDRAWN, terminal)
        Either<String, NegotiationLedger<String, ContractOffer>> withdrawResult = process.withdraw("session-3", "freelancer-A", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(withdrawResult.isRight());
        NegotiationLedger<String, ContractOffer> ledger = withdrawResult.getRight();

        assertEquals(NegotiationLedger.Status.WITHDRAWN, ledger.status());
        assertTrue(ledger.isTerminal());

        // Cannot accept after withdrawal
        Either<String, NegotiationLedger<String, ContractOffer>> acceptWithdrawn = process.accept("session-3", "client-B", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(acceptWithdrawn.isLeft());

        // Verify event published
        List<NegotiationEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(2, events.size()); // offer, withdraw
        assertTrue(events.get(1) instanceof NegotiationEvent.NegotiationWithdrawn);
    }
}