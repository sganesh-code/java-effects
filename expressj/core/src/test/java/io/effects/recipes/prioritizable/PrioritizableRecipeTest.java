package io.effects.recipes.prioritizable;

import io.effects.recipes.prioritizable.models.*;
import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrioritizableRecipeTest {

    static class TestProjector implements PriorityProjector<String, String, String> {
        String workId;
        PriorityLedger.Status status;
        String currentPriority;
        Instant deferredUntil;
        List<PriorityStep<String, String>> history;

        @Override
        public void project(
            String workId, 
            PriorityLedger.Status status, 
            String currentPriority, 
            Instant deferredUntil, 
            List<PriorityStep<String, String>> history
        ) {
            this.workId = workId;
            this.status = status;
            this.currentPriority = currentPriority;
            this.deferredUntil = deferredUntil;
            this.history = history;
        }
    }

    static record SupportCase(String category) implements PrioritizableRequest<String, String, String> {
        @Override
        public Either<String, Void> evaluateInitialPriority(PriorityLedger<String, String, String> ledger, String proposedPriority, Instant now) {
            if (!List.of("LOW", "MEDIUM", "HIGH").contains(proposedPriority.toUpperCase())) {
                return Either.left("Invalid priority level: " + proposedPriority);
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateReprioritization(PriorityLedger<String, String, String> ledger, String currentPriority, String proposedPriority, Instant now) {
            if (ledger.isExpedited()) {
                return Either.left("Cannot reprioritize an expedited support case");
            }
            if (!List.of("LOW", "MEDIUM", "HIGH").contains(proposedPriority.toUpperCase())) {
                return Either.left("Invalid priority level: " + proposedPriority);
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateDeferral(PriorityLedger<String, String, String> ledger, Instant deferredUntil, Instant now) {
            TestProjector projector = new TestProjector();
            ledger.projectState(projector);
            if ("HIGH".equalsIgnoreCase(projector.currentPriority)) {
                return Either.left("Cannot defer high priority support cases");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateExpedition(PriorityLedger<String, String, String> ledger, Instant now) {
            return Either.right(null);
        }
    }

    @Test
    void testSuccessfulInitialSequencingAndReprioritization() {
        InMemoryStateRepository<String, PriorityLedger<String, String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<PrioritizableEvent<String, String>> publisher = new InMemoryEventPublisher<>();
        PrioritizableProcess<String, String, String> process = new PrioritizableProcess<>(repo, publisher, new NoOpTelemetryPort());

        SupportCase supportCase = new SupportCase("Billing");
        process.register("case-101", supportCase).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T14:00:00Z");

        // 1. Initial sequence -> LOW
        Either<String, PriorityLedger<String, String, String>> seqRes = 
            process.sequence("case-101", "LOW", "Initial triage", now).unsafeRunSync();
        assertTrue(seqRes.isRight());

        PriorityLedger<String, String, String> ledger = seqRes.getRight();
        assertTrue(ledger.hasPriorityBeenSet());
        assertFalse(ledger.isExpedited());
        assertFalse(ledger.isCurrentlyDeferred(now));

        // State verification via projection
        TestProjector projector = new TestProjector();
        ledger.projectState(projector);
        assertEquals("case-101", projector.workId);
        assertEquals(PriorityLedger.Status.SEQUENCED, projector.status);
        assertEquals("LOW", projector.currentPriority);
        assertNull(projector.deferredUntil);
        assertEquals(1, projector.history.size());
        assertEquals(PriorityStep.Type.SEQUENCE, projector.history.get(0).type());

        // 2. Reprioritize -> MEDIUM
        Either<String, PriorityLedger<String, String, String>> reprioritizeRes = 
            process.reprioritize("case-101", "MEDIUM", "Customer requested escalation", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(reprioritizeRes.isRight());

        PriorityLedger<String, String, String> finalLedger = reprioritizeRes.getRight();
        TestProjector finalProjector = new TestProjector();
        finalLedger.projectState(finalProjector);
        assertEquals(PriorityLedger.Status.REPRIORITIZED, finalProjector.status);
        assertEquals("MEDIUM", finalProjector.currentPriority);
        assertEquals(2, finalProjector.history.size());
        assertEquals(PriorityStep.Type.REPRIORITIZE, finalProjector.history.get(1).type());

        // Verify published events
        List<PrioritizableEvent<String, String>> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof WorkSequenced);
        assertTrue(events.get(1) instanceof WorkReprioritized);
        WorkReprioritized<String, String> reprioritizedEvent = (WorkReprioritized<String, String>) events.get(1);
        assertEquals("LOW", reprioritizedEvent.previousPriority());
        assertEquals("MEDIUM", reprioritizedEvent.newPriority());
    }

    @Test
    void testDeferralValidationAndLaws() {
        InMemoryStateRepository<String, PriorityLedger<String, String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<PrioritizableEvent<String, String>> publisher = new InMemoryEventPublisher<>();
        PrioritizableProcess<String, String, String> process = new PrioritizableProcess<>(repo, publisher, new NoOpTelemetryPort());

        SupportCase lowCase = new SupportCase("SaaS");
        process.register("case-102", lowCase).unsafeRunSync();

        SupportCase highCase = new SupportCase("Infrastructure");
        process.register("case-103", highCase).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T14:10:00Z");

        // Sequence both cases
        process.sequence("case-102", "LOW", "Triage low", now).unsafeRunSync();
        process.sequence("case-103", "HIGH", "Triage high", now).unsafeRunSync();

        // 1. Defer lowCase -> should succeed
        Instant future = now.plusSeconds(3600);
        Either<String, PriorityLedger<String, String, String>> deferLowRes = 
            process.defer("case-102", future, "Postponed for client reply", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(deferLowRes.isRight());

        PriorityLedger<String, String, String> lowLedger = deferLowRes.getRight();
        assertTrue(lowLedger.isCurrentlyDeferred(now.plusSeconds(20)));
        assertFalse(lowLedger.isCurrentlyDeferred(future.plusSeconds(10))); // Exceeded deferral window

        TestProjector lowProjector = new TestProjector();
        lowLedger.projectState(lowProjector);
        assertEquals(PriorityLedger.Status.DEFERRED, lowProjector.status);
        assertEquals(future, lowProjector.deferredUntil);

        // 2. Defer highCase -> should fail (validation check via double dispatch state projection)
        Either<String, PriorityLedger<String, String, String>> deferHighRes = 
            process.defer("case-103", future, "Postpone critical", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(deferHighRes.isLeft());
        assertTrue(deferHighRes.getLeft().contains("Cannot defer high priority support cases"));
    }

    @Test
    void testExpeditionAndLaws() {
        InMemoryStateRepository<String, PriorityLedger<String, String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<PrioritizableEvent<String, String>> publisher = new InMemoryEventPublisher<>();
        PrioritizableProcess<String, String, String> process = new PrioritizableProcess<>(repo, publisher, new NoOpTelemetryPort());

        SupportCase supportCase = new SupportCase("Billing");
        process.register("case-104", supportCase).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T14:20:00Z");

        // Sequence
        process.sequence("case-104", "MEDIUM", "Triage medium", now).unsafeRunSync();

        // Expedite
        Either<String, PriorityLedger<String, String, String>> expediteRes = 
            process.expedite("case-104", "CRITICAL SLA EXCEEDED", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(expediteRes.isRight());

        PriorityLedger<String, String, String> ledger = expediteRes.getRight();
        assertTrue(ledger.isExpedited());

        TestProjector projector = new TestProjector();
        ledger.projectState(projector);
        assertEquals(PriorityLedger.Status.EXPEDITED, projector.status);

        // Cannot reprioritize an expedited case -> should fail
        Either<String, PriorityLedger<String, String, String>> reprioritizeRes = 
            process.reprioritize("case-104", "LOW", "Downgrade", now.plusSeconds(20)).unsafeRunSync();
        assertTrue(reprioritizeRes.isLeft());
        assertTrue(reprioritizeRes.getLeft().contains("Cannot reprioritize an expedited support case"));
    }
}
