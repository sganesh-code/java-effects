package io.effects.recipes.reconciliable;

import io.effects.recipes.reconciliable.models.*;
import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ReconciliationRecipeTest {

    static record BankTransaction(double amount, String merchant) {}

    static record BankReconciliation(double expectedAmount, String expectedMerchant)
        implements ReconciliableRequest<String, String, BankTransaction, String> {

        @Override
        public Either<String, Void> evaluateMatching(
            ReconciliationLedger<String, String, BankTransaction, String> ledger, 
            String itemId, 
            BankTransaction externalItem, 
            Instant now
        ) {
            if (expectedAmount != externalItem.amount()) {
                return Either.left("Transaction amount mismatch: expected " + expectedAmount + " but got " + externalItem.amount());
            }
            if (!Objects.equals(expectedMerchant, externalItem.merchant())) {
                return Either.left("Transaction merchant mismatch: expected " + expectedMerchant + " but got " + externalItem.merchant());
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateDiscrepancy(
            ReconciliationLedger<String, String, BankTransaction, String> ledger, 
            String itemId, 
            String discrepancyCode, 
            Instant now
        ) {
            if (!"AMOUNT_MISMATCH".equals(discrepancyCode) && !"MERCHANT_MISMATCH".equals(discrepancyCode)) {
                return Either.left("Unsupported discrepancy code: " + discrepancyCode);
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateResolution(
            ReconciliationLedger<String, String, BankTransaction, String> ledger, 
            String itemId, 
            String resolutionType, 
            String comment, 
            Instant now
        ) {
            if (!"MANUAL_ADJUSTMENT".equals(resolutionType) && !"AUTO_WRITE_OFF".equals(resolutionType)) {
                return Either.left("Unsupported resolution type: " + resolutionType);
            }
            if (comment.length() < 5) {
                return Either.left("Resolution comment must be at least 5 chars long");
            }
            return Either.right(null);
        }
    }

    @Test
    void testSuccessfulPerfectMatch() {
        InMemoryStateRepository<String, ReconciliationLedger<String, String, BankTransaction, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<ReconciliationEvent<String, String>> publisher = new InMemoryEventPublisher<>();
        ReconciliableProcess<String, String, BankTransaction, String> process = new ReconciliableProcess<>(repo, publisher, new NoOpTelemetryPort());

        BankReconciliation matchingCriteria = new BankReconciliation(45.50, "Starbucks");
        process.register("recon-001", matchingCriteria).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T11:00:00Z");
        BankTransaction externalTx = new BankTransaction(45.50, "Starbucks");

        // Execute match -> should succeed
        Either<String, ReconciliationLedger<String, String, BankTransaction, String>> result = 
            process.match("recon-001", "tx-999", externalTx, "Match found in bank statements", now).unsafeRunSync();
        assertTrue(result.isRight());

        ReconciliationLedger<String, String, BankTransaction, String> ledger = result.getRight();
        assertEquals(ReconciliationLedger.Status.MATCHED, ledger.status());
        assertEquals("tx-999", ledger.itemId());
        assertTrue(ledger.isTerminal());

        // Verify history step
        List<ReconciliationStep<String, String>> history = ledger.history();
        assertEquals(1, history.size());
        assertEquals(ReconciliationStep.Type.MATCH, history.get(0).type());
        assertEquals("tx-999", history.get(0).itemId());
        assertEquals("Match found in bank statements", history.get(0).comment());

        // Verify published events
        List<ReconciliationEvent<String, String>> events = publisher.getPublishedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof ItemMatched);
        ItemMatched<String, String> matchedEvent = (ItemMatched<String, String>) events.get(0);
        assertEquals("recon-001", matchedEvent.reconciliationId());
        assertEquals("tx-999", matchedEvent.itemId());
    }

    @Test
    void testAmountMismatchValidation() {
        ReconciliableProcess<String, String, BankTransaction, String> process = new ReconciliableProcess<>();
        BankReconciliation matchingCriteria = new BankReconciliation(45.50, "Starbucks");
        process.register("recon-002", matchingCriteria).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T11:05:00Z");
        BankTransaction badAmountTx = new BankTransaction(40.00, "Starbucks");

        // Execute match -> should fail with amount mismatch Left error
        Either<String, ReconciliationLedger<String, String, BankTransaction, String>> result = 
            process.match("recon-002", "tx-998", badAmountTx, "Match attempt with bad amount", now).unsafeRunSync();
        assertTrue(result.isLeft());
        assertTrue(result.getLeft().contains("Transaction amount mismatch: expected 45.5 but got 40.0"));
    }

    @Test
    void testDiscrepancyFlaggingAndResolution() {
        InMemoryStateRepository<String, ReconciliationLedger<String, String, BankTransaction, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<ReconciliationEvent<String, String>> publisher = new InMemoryEventPublisher<>();
        ReconciliableProcess<String, String, BankTransaction, String> process = new ReconciliableProcess<>(repo, publisher, new NoOpTelemetryPort());

        BankReconciliation matchingCriteria = new BankReconciliation(120.00, "Best Buy");
        process.register("recon-003", matchingCriteria).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T11:10:00Z");

        // 1. Flag discrepancy -> should succeed
        Either<String, ReconciliationLedger<String, String, BankTransaction, String>> discrepancyResult = 
            process.flagDiscrepancy("recon-003", "tx-997", "AMOUNT_MISMATCH", "Discrepancy noted on transaction", now).unsafeRunSync();
        assertTrue(discrepancyResult.isRight());

        ReconciliationLedger<String, String, BankTransaction, String> ledger = discrepancyResult.getRight();
        assertEquals(ReconciliationLedger.Status.DISCREPANCY, ledger.status());
        assertEquals("tx-997", ledger.itemId());
        assertEquals("AMOUNT_MISMATCH", ledger.discrepancyCode());
        assertFalse(ledger.isTerminal());

        // 2. Attempt to resolve with invalid resolution type -> should fail
        Either<String, ReconciliationLedger<String, String, BankTransaction, String>> badTypeResult = 
            process.resolve("recon-003", "tx-997", "BAD_RESOLUTION", "Just adjusted it", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(badTypeResult.isLeft());
        assertTrue(badTypeResult.getLeft().contains("Unsupported resolution type"));

        // 3. Attempt to resolve with too short explanation -> should fail
        Either<String, ReconciliationLedger<String, String, BankTransaction, String>> badCommentResult = 
            process.resolve("recon-003", "tx-997", "MANUAL_ADJUSTMENT", "Ok", now.plusSeconds(20)).unsafeRunSync();
        assertTrue(badCommentResult.isLeft());
        assertTrue(badCommentResult.getLeft().contains("Resolution comment must be at least 5 chars long"));

        // 4. Resolve successfully with valid params -> should succeed
        Either<String, ReconciliationLedger<String, String, BankTransaction, String>> resolveResult = 
            process.resolve("recon-003", "tx-997", "MANUAL_ADJUSTMENT", "Merchant provided coupon refund", now.plusSeconds(30)).unsafeRunSync();
        assertTrue(resolveResult.isRight());

        ReconciliationLedger<String, String, BankTransaction, String> finalLedger = resolveResult.getRight();
        assertEquals(ReconciliationLedger.Status.RESOLVED, finalLedger.status());
        assertEquals("MANUAL_ADJUSTMENT", finalLedger.resolutionType());
        assertTrue(finalLedger.isTerminal());

        // Verify history steps count (DISCREPANCY, RESOLVE)
        List<ReconciliationStep<String, String>> history = finalLedger.history();
        assertEquals(2, history.size());
        assertEquals(ReconciliationStep.Type.DISCREPANCY, history.get(0).type());
        assertEquals(ReconciliationStep.Type.RESOLVE, history.get(1).type());
        assertEquals("Merchant provided coupon refund", history.get(1).comment());

        // Verify events published (DiscrepancyFlagged, DiscrepancyResolved)
        List<ReconciliationEvent<String, String>> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof DiscrepancyFlagged);
        assertTrue(events.get(1) instanceof DiscrepancyResolved);

        DiscrepancyResolved<String, String> resolvedEvent = (DiscrepancyResolved<String, String>) events.get(1);
        assertEquals("recon-003", resolvedEvent.reconciliationId());
        assertEquals("MANUAL_ADJUSTMENT", resolvedEvent.resolutionType());
    }

    @Test
    void testTerminalMatchingPrevention() {
        InMemoryStateRepository<String, ReconciliationLedger<String, String, BankTransaction, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<ReconciliationEvent<String, String>> publisher = new InMemoryEventPublisher<>();
        ReconciliableProcess<String, String, BankTransaction, String> process = new ReconciliableProcess<>(repo, publisher, new NoOpTelemetryPort());

        BankReconciliation matchingCriteria = new BankReconciliation(50.00, "Uber");
        process.register("recon-004", matchingCriteria).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T11:20:00Z");
        BankTransaction externalTx = new BankTransaction(50.00, "Uber");

        // Match first to reach terminal status MATCHED
        process.match("recon-004", "tx-996", externalTx, "Match found", now).unsafeRunSync();

        // Try to flag discrepancy on matched -> should fail
        Either<String, ReconciliationLedger<String, String, BankTransaction, String>> badDiscrepancy = 
            process.flagDiscrepancy("recon-004", "tx-996", "AMOUNT_MISMATCH", "Belated flag", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(badDiscrepancy.isLeft());
        assertTrue(badDiscrepancy.getLeft().contains("Cannot flag discrepancy on a terminal reconciliation ledger"));

        // Try to match again -> should fail
        Either<String, ReconciliationLedger<String, String, BankTransaction, String>> badMatchAgain = 
            process.match("recon-004", "tx-996", externalTx, "Match again", now.plusSeconds(20)).unsafeRunSync();
        assertTrue(badMatchAgain.isLeft());
        assertTrue(badMatchAgain.getLeft().contains("Cannot match on a terminal reconciliation ledger"));
    }
}
