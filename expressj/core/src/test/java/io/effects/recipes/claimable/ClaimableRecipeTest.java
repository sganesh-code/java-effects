package io.effects.recipes.claimable;

import io.effects.recipes.claimable.models.*;
import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClaimableRecipeTest {

    static record InsuranceClaim(double claimAmount, String medicalCode) 
        implements ClaimableRequest<String, String, String> {

        @Override
        public Either<String, Void> evaluateFile(ClaimLedger<String, String, String> ledger, String claimantId, String comment, Instant now) {
            if (claimAmount <= 0) {
                return Either.left("Claim amount must be positive");
            }
            if (comment.length() < 5) {
                return Either.left("Filing comment must be descriptive");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateReview(ClaimLedger<String, String, String> ledger, String reviewerId, String validatorRole, String comment, Instant now) {
            if (claimAmount >= 1000.0 && !"MD".equals(validatorRole)) {
                return Either.left("Claims over $1000 require an MD auditor to review");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateDecision(ClaimLedger<String, String, String> ledger, String reviewerId, String validatorRole, boolean accept, String comment, Instant now) {
            if (!accept && comment.length() < 10) {
                return Either.left("Denial reasons must be detailed (at least 10 chars)");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateDispute(ClaimLedger<String, String, String> ledger, String actorId, String comment, Instant now) {
            if (comment.length() < 10) {
                return Either.left("Dispute explanation must be descriptive");
            }
            return Either.right(null);
        }
    }

    @Test
    void testDirectAcceptPath() {
        InMemoryStateRepository<String, ClaimLedger<String, String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<ClaimableEvent<String>> publisher = new InMemoryEventPublisher<>();
        ClaimableProcess<String, String, String> process = new ClaimableProcess<>(repo, publisher, new NoOpTelemetryPort());

        InsuranceClaim claim = new InsuranceClaim(500.0, "99213");
        process.register("claim-001", claim).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T13:00:00Z");

        // 1. File Claim
        Either<String, ClaimLedger<String, String, String>> fileRes = 
            process.file("claim-001", "claimant-abc", "Filing standard office visit claim", now).unsafeRunSync();
        assertTrue(fileRes.isRight());
        assertEquals(ClaimLedger.Status.FILED, fileRes.getRight().status());

        // 2. Initiate Review
        Either<String, ClaimLedger<String, String, String>> reviewRes = 
            process.review("claim-001", "reviewer-nurse", "RN", "Reviewing office visit records", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(reviewRes.isRight());
        assertEquals(ClaimLedger.Status.UNDER_REVIEW, reviewRes.getRight().status());

        // 3. Accept Claim
        Either<String, ClaimLedger<String, String, String>> acceptRes = 
            process.accept("claim-001", "reviewer-nurse", "RN", "Matches criteria", now.plusSeconds(20)).unsafeRunSync();
        assertTrue(acceptRes.isRight());

        ClaimLedger<String, String, String> ledger = acceptRes.getRight();
        assertEquals(ClaimLedger.Status.ACCEPTED, ledger.status());
        assertTrue(ledger.isTerminal());

        // Verify history steps
        List<ClaimStep<String>> history = ledger.history();
        assertEquals(3, history.size());
        assertEquals(ClaimStep.Type.FILE, history.get(0).type());
        assertEquals(ClaimStep.Type.REVIEW, history.get(1).type());
        assertEquals(ClaimStep.Type.ACCEPT, history.get(2).type());

        // Verify published events
        List<ClaimableEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(3, events.size());
        assertTrue(events.get(0) instanceof ClaimFiled);
        assertTrue(events.get(1) instanceof ClaimUnderReview);
        assertTrue(events.get(2) instanceof ClaimAccepted);
    }

    @Test
    void testMDRequiredForHighAmountClaims() {
        ClaimableProcess<String, String, String> process = new ClaimableProcess<>();
        InsuranceClaim highClaim = new InsuranceClaim(1500.0, "33510");
        process.register("claim-002", highClaim).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T13:05:00Z");

        // 1. File Claim
        process.file("claim-002", "claimant-xyz", "Filing heart surgery bypass claim", now).unsafeRunSync();

        // 2. Try reviewing with Nurse (RN) -> should fail
        Either<String, ClaimLedger<String, String, String>> reviewFail = 
            process.review("claim-002", "reviewer-nurse", "RN", "Routine audit", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(reviewFail.isLeft());
        assertTrue(reviewFail.getLeft().contains("require an MD auditor to review"));

        // 3. Review with Medical Director (MD) -> should succeed
        Either<String, ClaimLedger<String, String, String>> reviewOk = 
            process.review("claim-002", "reviewer-director", "MD", "Surgery chart audit", now.plusSeconds(20)).unsafeRunSync();
        assertTrue(reviewOk.isRight());
        assertEquals(ClaimLedger.Status.UNDER_REVIEW, reviewOk.getRight().status());
    }

    @Test
    void testDenialDisputeAndReevaluationFlow() {
        InMemoryStateRepository<String, ClaimLedger<String, String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<ClaimableEvent<String>> publisher = new InMemoryEventPublisher<>();
        ClaimableProcess<String, String, String> process = new ClaimableProcess<>(repo, publisher, new NoOpTelemetryPort());

        InsuranceClaim claim = new InsuranceClaim(600.0, "99214");
        process.register("claim-003", claim).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T13:10:00Z");

        // 1. File & Review
        process.file("claim-003", "claimant-abc", "Filing medical claim", now).unsafeRunSync();
        process.review("claim-003", "reviewer-nurse", "RN", "Nurse review", now.plusSeconds(10)).unsafeRunSync();

        // 2. Deny with too short reason -> should fail
        Either<String, ClaimLedger<String, String, String>> denyFail = 
            process.deny("claim-003", "reviewer-nurse", "RN", "No", now.plusSeconds(20)).unsafeRunSync();
        assertTrue(denyFail.isLeft());
        assertTrue(denyFail.getLeft().contains("Denial reasons must be detailed"));

        // 3. Deny with detailed reason -> should succeed
        Either<String, ClaimLedger<String, String, String>> denyOk = 
            process.deny("claim-003", "reviewer-nurse", "RN", "Incomplete documentation supplied", now.plusSeconds(30)).unsafeRunSync();
        assertTrue(denyOk.isRight());
        assertEquals(ClaimLedger.Status.DENIED, denyOk.getRight().status());

        // 4. Dispute denial with too short reason -> should fail
        Either<String, ClaimLedger<String, String, String>> disputeFail = 
            process.dispute("claim-003", "claimant-abc", "Reopen", now.plusSeconds(40)).unsafeRunSync();
        assertTrue(disputeFail.isLeft());
        assertTrue(disputeFail.getLeft().contains("Dispute explanation must be descriptive"));

        // 5. Dispute successfully with detailed explanation -> transitions to DISPUTED
        Either<String, ClaimLedger<String, String, String>> disputeOk = 
            process.dispute("claim-003", "claimant-abc", "Sending signed medical authorization charts", now.plusSeconds(50)).unsafeRunSync();
        assertTrue(disputeOk.isRight());
        assertEquals(ClaimLedger.Status.DISPUTED, disputeOk.getRight().status());

        // 6. Re-review by MD
        process.review("claim-003", "reviewer-director", "MD", "MD audit of charts", now.plusSeconds(60)).unsafeRunSync();

        // 7. Accept Claim
        Either<String, ClaimLedger<String, String, String>> finalAccept = 
            process.accept("claim-003", "reviewer-director", "MD", "Charts verify medical necessity", now.plusSeconds(70)).unsafeRunSync();
        assertTrue(finalAccept.isRight());

        ClaimLedger<String, String, String> finalLedger = finalAccept.getRight();
        assertEquals(ClaimLedger.Status.ACCEPTED, finalLedger.status());
        assertTrue(finalLedger.isTerminal());

        // Verify final timeline size: FILE, REVIEW, DENY, DISPUTE, REVIEW, ACCEPT = 6 steps
        assertEquals(6, finalLedger.history().size());
        assertEquals(ClaimStep.Type.DENY, finalLedger.history().get(2).type());
        assertEquals(ClaimStep.Type.DISPUTE, finalLedger.history().get(3).type());
        assertEquals(ClaimStep.Type.REVIEW, finalLedger.history().get(4).type());
        assertEquals(ClaimStep.Type.ACCEPT, finalLedger.history().get(5).type());

        // Verify events emitted contains ClaimDisputed
        List<ClaimableEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(6, events.size());
        assertTrue(events.get(3) instanceof ClaimDisputed);
    }
}
