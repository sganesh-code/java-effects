package io.effects.recipes.escalatable;

import io.effects.recipes.escalatable.models.*;
import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EscalatableRecipeTest {

    static class TestEscalationProjector implements EscalationProjector<String, String, String> {
        String caseId;
        EscalationLedger.Status status;
        String currentTier;
        String currentHandlerId;
        List<EscalationStep<String, String>> history;

        @Override
        public void project(
            String caseId, 
            EscalationLedger.Status status, 
            String currentTier, 
            String currentHandlerId, 
            List<EscalationStep<String, String>> history
        ) {
            this.caseId = caseId;
            this.status = status;
            this.currentTier = currentTier;
            this.currentHandlerId = currentHandlerId;
            this.history = history;
        }
    }

    static record SupportTicketCase(String priority) implements EscalatableRequest<String, String, String> {
        @Override
        public Either<String, Void> evaluateFile(EscalationLedger<String, String, String> ledger, String proposedTier, String handlerId, Instant now) {
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateSLAWarning(EscalationLedger<String, String, String> ledger, Instant deadline, Instant now) {
            if (now.isAfter(deadline)) {
                return Either.left("SLA deadline has already been breached");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateEscalation(EscalationLedger<String, String, String> ledger, String currentTier, String proposedTier, Instant now) {
            if ("Tier-3".equalsIgnoreCase(currentTier)) {
                return Either.left("Cannot escalate past Tier-3");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateDeescalation(EscalationLedger<String, String, String> ledger, String currentTier, String proposedTier, Instant now) {
            if ("HIGH".equalsIgnoreCase(priority)) {
                return Either.left("High priority support cases cannot be de-escalated");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateReassignment(EscalationLedger<String, String, String> ledger, String currentHandlerId, String proposedHandlerId, Instant now) {
            return Either.right(null);
        }
    }

    @Test
    void testFileAndSuccessfulEscalation() {
        InMemoryStateRepository<String, EscalationLedger<String, String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<EscalatableEvent<String, String>> publisher = new InMemoryEventPublisher<>();
        EscalatableProcess<String, String, String> process = new EscalatableProcess<>(repo, publisher, new NoOpTelemetryPort());

        SupportTicketCase ticket = new SupportTicketCase("MEDIUM");
        process.register("case-301", ticket).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T16:00:00Z");

        // 1. File Case
        Either<String, EscalationLedger<String, String, String>> fileRes = 
            process.file("case-301", "Tier-1", "agent-1", "Standard filing", now).unsafeRunSync();
        assertTrue(fileRes.isRight());

        EscalationLedger<String, String, String> ledger = fileRes.getRight();
        assertTrue(ledger.isFiled());
        assertFalse(ledger.isTerminal());

        // State verification via projection
        TestEscalationProjector projector = new TestEscalationProjector();
        ledger.projectState(projector);
        assertEquals("case-301", projector.caseId);
        assertEquals(EscalationLedger.Status.STANDARD, projector.status);
        assertEquals("Tier-1", projector.currentTier);
        assertEquals("agent-1", projector.currentHandlerId);
        assertEquals(1, projector.history.size());
        assertEquals(EscalationStep.Type.INITIALIZE, projector.history.get(0).type());

        // 2. Escalate Case
        Either<String, EscalationLedger<String, String, String>> escalateRes = 
            process.escalate("case-301", "Tier-2", "Requires senior agent", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(escalateRes.isRight());

        EscalationLedger<String, String, String> finalLedger = escalateRes.getRight();
        TestEscalationProjector finalProjector = new TestEscalationProjector();
        finalLedger.projectState(finalProjector);
        assertEquals(EscalationLedger.Status.ESCALATED, finalProjector.status);
        assertEquals("Tier-2", finalProjector.currentTier);
        assertEquals(2, finalProjector.history.size());
        assertEquals(EscalationStep.Type.ESCALATE, finalProjector.history.get(1).type());

        // Verify published events
        List<EscalatableEvent<String, String>> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof CaseEscalated);
        assertTrue(events.get(1) instanceof CaseEscalated);
    }

    @Test
    void testSLAWarningBreachCheck() {
        InMemoryStateRepository<String, EscalationLedger<String, String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<EscalatableEvent<String, String>> publisher = new InMemoryEventPublisher<>();
        EscalatableProcess<String, String, String> process = new EscalatableProcess<>(repo, publisher, new NoOpTelemetryPort());

        SupportTicketCase ticket = new SupportTicketCase("MEDIUM");
        process.register("case-302", ticket).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T16:05:00Z");
        Instant deadline = now.plusSeconds(3600); // 1 hour SLA

        process.file("case-302", "Tier-1", "agent-2", "Standard filing", now).unsafeRunSync();

        // 1. Trigger SLA warning before deadline -> should succeed
        Either<String, EscalationLedger<String, String, String>> warningRes = 
            process.triggerSLAWarning("case-302", deadline, "Approaching deadline warning", now.plusSeconds(1800)).unsafeRunSync();
        assertTrue(warningRes.isRight());

        TestEscalationProjector projector = new TestEscalationProjector();
        warningRes.getRight().projectState(projector);
        assertEquals(EscalationLedger.Status.SLA_WARNING, projector.status);

        // 2. Trigger SLA warning after deadline -> should fail (validation check)
        Either<String, EscalationLedger<String, String, String>> badWarningRes = 
            process.triggerSLAWarning("case-302", deadline, "Late warning attempt", deadline.plusSeconds(10)).unsafeRunSync();
        assertTrue(badWarningRes.isLeft());
        assertTrue(badWarningRes.getLeft().contains("SLA deadline has already been breached"));
    }

    @Test
    void testReassignmentAndDeescalationValidation() {
        InMemoryStateRepository<String, EscalationLedger<String, String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<EscalatableEvent<String, String>> publisher = new InMemoryEventPublisher<>();
        EscalatableProcess<String, String, String> process1 = new EscalatableProcess<>(repo, publisher, new NoOpTelemetryPort());
        EscalatableProcess<String, String, String> process2 = new EscalatableProcess<>(repo, publisher, new NoOpTelemetryPort());

        SupportTicketCase lowTicket = new SupportTicketCase("LOW");
        process1.register("case-303", lowTicket).unsafeRunSync();

        SupportTicketCase highTicket = new SupportTicketCase("HIGH");
        process2.register("case-304", highTicket).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T16:10:00Z");

        // File both cases at Tier-2
        process1.file("case-303", "Tier-2", "agent-3", "Filing low ticket", now).unsafeRunSync();
        process2.file("case-304", "Tier-2", "agent-4", "Filing high ticket", now).unsafeRunSync();

        // 1. Reassign lowTicket -> should succeed
        Either<String, EscalationLedger<String, String, String>> reassignRes = 
            process1.reassign("case-303", "agent-specialist", "Transfer to expert", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(reassignRes.isRight());

        TestEscalationProjector lowProjector = new TestEscalationProjector();
        reassignRes.getRight().projectState(lowProjector);
        assertEquals("agent-specialist", lowProjector.currentHandlerId);
        assertEquals(EscalationLedger.Status.REASSIGNED, lowProjector.status);

        // 2. Try de-escalating highTicket from Tier-2 to Tier-1 -> should fail
        Either<String, EscalationLedger<String, String, String>> deescalateHigh = 
            process2.deescalate("case-304", "Tier-1", "Reduce priority cost", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(deescalateHigh.isLeft());
        assertTrue(deescalateHigh.getLeft().contains("High priority support cases cannot be de-escalated"));

        // 3. De-escalate lowTicket from Tier-2 to Tier-1 -> should succeed
        Either<String, EscalationLedger<String, String, String>> deescalateLow = 
            process1.deescalate("case-303", "Tier-1", "Standard triage downgrade", now.plusSeconds(20)).unsafeRunSync();
        assertTrue(deescalateLow.isRight());

        TestEscalationProjector lowFinalProjector = new TestEscalationProjector();
        deescalateLow.getRight().projectState(lowFinalProjector);
        assertEquals("Tier-1", lowFinalProjector.currentTier);
        assertEquals(EscalationLedger.Status.STANDARD, lowFinalProjector.status);
    }
}
