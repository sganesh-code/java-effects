package io.effects.recipes.auditable;

import io.effects.recipes.auditable.models.*;

import io.effects.core.Either;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditableRecipeTest {

    // A custom, clean, non-anemic business state representation S.
    private record DocumentState(String content, int version) {}

    // A custom, clean, non-anemic audit event detail representation E.
    private record EditAction(String appendText, int versionIncrement) {}

    // A concrete, behavioral domain request representing an online collaborative document.
    private static final class CollaborativeDocument implements AuditableRequest<String, EditAction, DocumentState> {

        CollaborativeDocument() {}

        @Override
        public Either<String, Void> evaluateEntry(AuditLedger<String, EditAction> ledger, EditAction detail, Instant now) {
            if (detail.appendText() == null || detail.appendText().isBlank()) {
                return Either.left("Cannot append blank or empty text to the document audit trail");
            }
            return Either.right(null);
        }

        @Override
        public DocumentState reconstructState(List<AuditStep<EditAction>> history) {
            StringBuilder contentBuilder = new StringBuilder();
            int currentVersion = 0;
            for (AuditStep<EditAction> step : history) {
                contentBuilder.append(step.detail().appendText());
                currentVersion += step.detail().versionIncrement();
            }
            return new DocumentState(contentBuilder.toString(), currentVersion);
        }

        @Override
        public String explainDecision(List<AuditStep<EditAction>> history, String decisionStepId) {
            return history.stream()
                .filter(step -> step.stepId().equals(decisionStepId))
                .findFirst()
                .map(step -> "On " + step.timestamp() + ", actor " + step.actorId() + " appended '" + step.detail().appendText() + "'")
                .orElse("Decision step ID not found in audit trail: " + decisionStepId);
        }
    }

    // 1. Initial Recording & Cryptographic Chaining Law Verification
    @Test
    void testAuditLogRecordingAndCryptography() {
        AuditableProcess<String, EditAction, DocumentState> process = new AuditableProcess<>();
        CollaborativeDocument document = new CollaborativeDocument();
        process.register("doc-1", document).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T14:00:00Z");

        process.initiate("doc-1").unsafeRunSync();

        // Fails because appended text is blank
        Either<String, AuditStep<EditAction>> badRecord = process.register("doc-1", "user-A", new EditAction("  ", 1), t0).unsafeRunSync();
        assertTrue(badRecord.isLeft());
        assertTrue(badRecord.getLeft().contains("Cannot append blank or empty text"));

        // Step 1: Alice appends "Hello "
        Either<String, AuditStep<EditAction>> success1 = process.register("doc-1", "user-A", new EditAction("Hello ", 1), t0).unsafeRunSync();
        assertTrue(success1.isRight());
        AuditStep<EditAction> step1 = success1.getRight();

        // Step 2: Bob appends "World!"
        Either<String, AuditStep<EditAction>> success2 = process.register("doc-1", "user-B", new EditAction("World!", 1), t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(success2.isRight());
        AuditStep<EditAction> step2 = success2.getRight();

        // Cryptographic Chain Validation
        assertNotNull(step1.hash());
        assertNotNull(step2.hash());
        assertNotEquals(step1.hash(), step2.hash());

        // Recalculating hash of Step 2 locally using Step 1's hash as previousHash
        String recalculatedStep2Hash = AuditStep.computeHash(
            step2.stepId(),
            step2.actorId(),
            step2.detail(),
            step1.hash(),
            step2.timestamp()
        );
        assertEquals(step2.hash(), recalculatedStep2Hash);
    }

    // 2. Replay State Reconstruction Law
    @Test
    void testReplayStateReconstruction() {
        AuditableProcess<String, EditAction, DocumentState> process = new AuditableProcess<>();
        CollaborativeDocument document = new CollaborativeDocument();
        process.register("doc-2", document).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T15:00:00Z");

        process.initiate("doc-2").unsafeRunSync();

        process.register("doc-2", "user-A", new EditAction("A", 1), t0).unsafeRunSync();
        process.register("doc-2", "user-B", new EditAction("B", 1), t0.plusSeconds(10)).unsafeRunSync();
        process.register("doc-2", "user-C", new EditAction("C", 1), t0.plusSeconds(20)).unsafeRunSync();

        // Replay history
        Either<String, DocumentState> replayResult = process.replay("doc-2").unsafeRunSync();
        assertTrue(replayResult.isRight());
        DocumentState state = replayResult.getRight();

        assertEquals("ABC", state.content());
        assertEquals(3, state.version());
    }

    // 3. Point-in-Time Explanation Law
    @Test
    void testAuditDecisionExplanation() {
        AuditableProcess<String, EditAction, DocumentState> process = new AuditableProcess<>();
        CollaborativeDocument document = new CollaborativeDocument();
        process.register("doc-3", document).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T16:00:00Z");

        process.initiate("doc-3").unsafeRunSync();

        Either<String, AuditStep<EditAction>> success = process.register("doc-3", "user-A", new EditAction("A", 1), t0).unsafeRunSync();
        assertTrue(success.isRight());
        AuditStep<EditAction> step = success.getRight();

        // Explain Decision Step
        Either<String, String> explanation = process.explain("doc-3", step.stepId()).unsafeRunSync();
        assertTrue(explanation.isRight());
        assertTrue(explanation.getRight().contains("actor user-A appended 'A'"));

        // Explain Non-existent Step
        Either<String, String> badExplanation = process.explain("doc-3", "non-existent-id").unsafeRunSync();
        assertTrue(badExplanation.isRight());
        assertTrue(badExplanation.getRight().contains("Decision step ID not found"));
    }

    // 4. Performance Optimization Snapshotting & Compaction Law
    @Test
    void testPerformanceSnapshottingAndCompaction() {
        InMemoryStateRepository<String, AuditLedger<String, EditAction>> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<AuditableEvent<String>> publisher = new InMemoryEventPublisher<>();
        AuditableProcess<String, EditAction, DocumentState> process = new AuditableProcess<>(repository, publisher, new NoOpTelemetryPort());

        CollaborativeDocument document = new CollaborativeDocument();
        process.register("doc-4", document).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T17:00:00Z");

        process.initiate("doc-4").unsafeRunSync();

        process.register("doc-4", "user-A", new EditAction("A", 1), t0).unsafeRunSync();
        process.register("doc-4", "user-B", new EditAction("B", 1), t0.plusSeconds(10)).unsafeRunSync();

        // Snapshotting and Ledger Compaction
        Either<String, DocumentState> snapshotResult = process.snapshot("doc-4", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(snapshotResult.isRight());
        DocumentState state = snapshotResult.getRight();

        assertEquals("AB", state.content());
        assertEquals(2, state.version());

        // Confirm Event Publication
        List<AuditableEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(3, events.size()); // register A, register B, snapshot taken
        assertTrue(events.get(2) instanceof AuditableEvent.SnapshotTaken);

        // Fetch the persisted ledger directly to confirm that its in-memory steps were compacted/cleared
        java.util.Optional<AuditLedger<String, EditAction>> loadedLedger = repository.find("doc-4").unsafeRunSync();
        assertTrue(loadedLedger.isPresent());
        assertTrue(loadedLedger.get().history().isEmpty()); // Compacted successfully!
    }
}