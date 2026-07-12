package io.effects.recipes.meterable;

import io.effects.Either;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MeterableRecipeTest {

    // A custom, clean, non-anemic representation of consumption usage ticks U.
    private record ApiUsage(int callsCount, String apiName) {}

    // A custom, clean, non-anemic representation of rating results R.
    private record BillInvoice(double totalPrice, String currency) {}

    // A concrete, behavioral domain request representing a SaaS API subscription plan.
    private static final class SaasBillingPlan implements MeterableRequest<String, ApiUsage, BillInvoice> {

        SaasBillingPlan() {}

        @Override
        public Either<String, Void> evaluateUsage(MeterLedger<String, ApiUsage> ledger, ApiUsage metric, Instant now) {
            if (metric.callsCount() <= 0) {
                return Either.left("Usage tick calls count must be positive");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, BillInvoice> evaluateRating(MeterLedger<String, ApiUsage> ledger, Instant now) {
            // Reconstruct usage total from history using double-dispatch
            int totalCalls = ledger.history().stream()
                .mapToInt(step -> step.metric().callsCount())
                .sum();

            // Pricing Law: first 100 calls are free, then $0.05 per call.
            double price = 0.0;
            if (totalCalls > 100) {
                price = (totalCalls - 100) * 0.05;
            }
            return Either.right(new BillInvoice(price, "USD"));
        }
    }

    // 1. Cycle Starts & Chronological Consumption Logs
    @Test
    void testMeterStartingAndContinuousUsage() {
        MeterableProcess<String, ApiUsage, BillInvoice> process = new MeterableProcess<>();
        SaasBillingPlan plan = new SaasBillingPlan();
        process.register("acc-1", plan).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T14:00:00Z");

        process.initiate("acc-1").unsafeRunSync();

        // Cannot register usage before meter is started
        Either<String, UsageStep<ApiUsage>> badUsage1 = process.recordUsage("acc-1", new ApiUsage(50, "translate"), t0).unsafeRunSync();
        assertTrue(badUsage1.isLeft());
        assertTrue(badUsage1.getLeft().contains("billing meter is not ACTIVE"));

        // Start meter -> succeeds
        Either<String, MeterLedger<String, ApiUsage>> startResult = process.start("acc-1", t0).unsafeRunSync();
        assertTrue(startResult.isRight());
        assertEquals(MeterLedger.Status.ACTIVE, startResult.getRight().status());

        // Fails because calls count is negative
        Either<String, UsageStep<ApiUsage>> badUsage2 = process.recordUsage("acc-1", new ApiUsage(-10, "translate"), t0.plusSeconds(5)).unsafeRunSync();
        assertTrue(badUsage2.isLeft());
        assertTrue(badUsage2.getLeft().contains("Usage tick calls count must be positive"));

        // Record valid usage
        Either<String, UsageStep<ApiUsage>> stepResult1 = process.recordUsage("acc-1", new ApiUsage(60, "translate"), t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(stepResult1.isRight());
        assertEquals(60, stepResult1.getRight().metric().callsCount());
    }

    // 2. Billing Cycle Finalization, Rating Plan Late Binding, and Compaction
    @Test
    void testBillingCycleRatingAndCompaction() {
        InMemoryStateRepository<String, MeterLedger<String, ApiUsage>> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<MeterableEvent<String>> publisher = new InMemoryEventPublisher<>();
        MeterableProcess<String, ApiUsage, BillInvoice> process = new MeterableProcess<>(repository, publisher, new NoOpTelemetryPort());

        SaasBillingPlan plan = new SaasBillingPlan();
        process.register("acc-2", plan).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T15:00:00Z");

        process.initiate("acc-2").unsafeRunSync();
        process.start("acc-2", t0).unsafeRunSync();

        // Record 120 total calls (60 + 60)
        process.recordUsage("acc-2", new ApiUsage(60, "translate"), t0.plusSeconds(10)).unsafeRunSync();
        process.recordUsage("acc-2", new ApiUsage(60, "speech-to-text"), t0.plusSeconds(20)).unsafeRunSync();

        // Rate the billing cycle -> succeeds (finalizes cycle)
        // 120 total calls: 100 free, 20 charged at $0.05 = $1.00 total bill
        Either<String, BillInvoice> ratingResult = process.rate("acc-2", t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(ratingResult.isRight());
        BillInvoice invoice = ratingResult.getRight();

        assertEquals(1.00, invoice.totalPrice());
        assertEquals("USD", invoice.currency());

        // Post-Billing Finality Lock: Cannot register new usage in finalized terminal cycle
        Either<String, UsageStep<ApiUsage>> postBillingUsage = process.recordUsage("acc-2", new ApiUsage(30, "translate"), t0.plusSeconds(40)).unsafeRunSync();
        assertTrue(postBillingUsage.isLeft());

        // Verify Event Publication
        List<MeterableEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(4, events.size()); // start, register 1, register 2, rate
        assertTrue(events.get(3) instanceof MeterableEvent.MeterRated);
        assertEquals(invoice, ((MeterableEvent.MeterRated<String, BillInvoice>) events.get(3)).rating());

        // Verification of Compaction Memory Footprint Optimization
        Optional<MeterLedger<String, ApiUsage>> loadedLedger = repository.find("acc-2").unsafeRunSync();
        assertTrue(loadedLedger.isPresent());
        assertEquals(MeterLedger.Status.FINALIZED, loadedLedger.get().status());
        assertTrue(loadedLedger.get().history().isEmpty()); // Compacted and cleared successfully!
    }
}