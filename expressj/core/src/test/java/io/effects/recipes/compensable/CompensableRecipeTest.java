package io.effects.recipes.compensable;

import io.effects.recipes.compensable.models.*;
import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class CompensableRecipeTest {

    static class TestCompProjector implements CompensationProjector<String, String> {
        String transactionId;
        CompensationLedger.Status status;
        List<String> completedSteps;
        List<SagaStep<String>> history;

        @Override
        public void project(
            String transactionId, 
            CompensationLedger.Status status, 
            List<String> completedSteps, 
            List<SagaStep<String>> history
        ) {
            this.transactionId = transactionId;
            this.status = status;
            this.completedSteps = completedSteps;
            this.history = history;
        }
    }

    static record CheckoutSaga(double amount) implements CompensableRequest<String, String> {
        @Override
        public Either<String, Void> evaluateStepExecution(CompensationLedger<String, String> ledger, String stepId, Instant now) {
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateRollbackTrigger(CompensationLedger<String, String> ledger, String failedStepId, String reason, Instant now) {
            return Either.right(null);
        }
    }

    @Test
    void testSuccessfulSagaCompletion() {
        InMemoryStateRepository<String, CompensationLedger<String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<CompensableEvent<String>> publisher = new InMemoryEventPublisher<>();
        CompensableProcess<String, String> process = new CompensableProcess<>(repo, publisher, new NoOpTelemetryPort());

        CheckoutSaga saga = new CheckoutSaga(150.00);
        process.register("tx-201", saga).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T15:00:00Z");

        AtomicBoolean paymentCompensated = new AtomicBoolean(false);
        AtomicBoolean stockCompensated = new AtomicBoolean(false);

        IO<String> cardCharge = IO.of("Charge Successful");
        IO<Void> cardRefund = IO.delay(() -> { paymentCompensated.set(true); return null; });

        IO<String> stockAllocate = IO.of("Stock Allocated");
        IO<Void> stockRelease = IO.delay(() -> { stockCompensated.set(true); return null; });

        // 1. Run Step 1 (Payment)
        Either<String, String> paymentRes = process.runStep("tx-201", "payment", cardCharge, cardRefund, "Execute payment", now).unsafeRunSync();
        assertTrue(paymentRes.isRight());
        assertEquals("Charge Successful", paymentRes.getRight());

        // 2. Run Step 2 (Inventory)
        Either<String, String> stockRes = process.runStep("tx-201", "inventory", stockAllocate, stockRelease, "Allocate inventory", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(stockRes.isRight());
        assertEquals("Stock Allocated", stockRes.getRight());

        // 3. Complete Saga
        Either<String, CompensationLedger<String, String>> completeRes = process.completeSaga("tx-201", "Saga complete", now.plusSeconds(20)).unsafeRunSync();
        assertTrue(completeRes.isRight());

        CompensationLedger<String, String> ledger = completeRes.getRight();
        assertTrue(ledger.isTerminal());

        // State verification
        TestCompProjector projector = new TestCompProjector();
        ledger.projectState(projector);
        assertEquals("tx-201", projector.transactionId);
        assertEquals(CompensationLedger.Status.COMPLETED, projector.status);
        assertEquals(List.of("payment", "inventory"), projector.completedSteps);
        assertEquals(3, projector.history.size()); // payment, inventory, complete
        assertEquals(SagaStep.Type.STEP_SUCCESS, projector.history.get(2).type());

        // Verify compensations did NOT run
        assertFalse(paymentCompensated.get());
        assertFalse(stockCompensated.get());

        // Verify events published
        List<CompensableEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(3, events.size());
        assertTrue(events.get(0) instanceof SagaStepSucceeded);
        assertTrue(events.get(1) instanceof SagaStepSucceeded);
        assertTrue(events.get(2) instanceof SagaStepSucceeded);
    }

    @Test
    void testSagaRollbackUponFailure() {
        InMemoryStateRepository<String, CompensationLedger<String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<CompensableEvent<String>> publisher = new InMemoryEventPublisher<>();
        CompensableProcess<String, String> process = new CompensableProcess<>(repo, publisher, new NoOpTelemetryPort());

        CheckoutSaga saga = new CheckoutSaga(99.00);
        process.register("tx-202", saga).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T15:05:00Z");

        AtomicBoolean paymentCompensated = new AtomicBoolean(false);
        IO<String> cardCharge = IO.of("Charge Successful");
        IO<Void> cardRefund = IO.delay(() -> { paymentCompensated.set(true); return null; });

        IO<String> shippingAction = IO.failed(new RuntimeException("UPS service down"));
        IO<Void> shippingCancel = IO.of(null);

        // 1. Run Step 1 (Payment succeeds)
        process.runStep("tx-202", "payment", cardCharge, cardRefund, "Execute payment", now).unsafeRunSync();

        // 2. Run Step 2 (Shipping fails) -> should fail and trigger automated payment compensation
        Either<String, String> shippingRes = process.runStep("tx-202", "shipping", shippingAction, shippingCancel, "Create shipping labels", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(shippingRes.isLeft());
        assertTrue(shippingRes.getLeft().contains("UPS service down"));

        // Verify that payment compensation refund action WAS executed
        assertTrue(paymentCompensated.get());

        // Verify terminal state COMPENSATED
        CompensationLedger<String, String> ledger = repo.find("tx-202").unsafeRunSync().orElseThrow();
        assertTrue(ledger.isTerminal());

        TestCompProjector projector = new TestCompProjector();
        ledger.projectState(projector);
        assertEquals(CompensationLedger.Status.COMPENSATED, projector.status);
        assertEquals(3, projector.history.size()); // payment success, rollback trigger, compensation success
        assertEquals(SagaStep.Type.STEP_SUCCESS, projector.history.get(0).type());
        assertEquals(SagaStep.Type.ROLLBACK_TRIGGER, projector.history.get(1).type());
        assertEquals(SagaStep.Type.COMPENSATION_SUCCESS, projector.history.get(2).type());

        // Verify published events
        List<CompensableEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(3, events.size());
        assertTrue(events.get(0) instanceof SagaStepSucceeded);
        assertTrue(events.get(1) instanceof SagaRollbackTriggered);
        assertTrue(events.get(2) instanceof SagaCompensated);
    }

    @Test
    void testSagaCompensationFailure() {
        InMemoryStateRepository<String, CompensationLedger<String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<CompensableEvent<String>> publisher = new InMemoryEventPublisher<>();
        CompensableProcess<String, String> process = new CompensableProcess<>(repo, publisher, new NoOpTelemetryPort());

        CheckoutSaga saga = new CheckoutSaga(450.00);
        process.register("tx-203", saga).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T15:10:00Z");

        IO<String> cardCharge = IO.of("Charge Successful");
        // Payment compensation refund fails/throws exception!
        IO<Void> cardRefund = IO.failed(new RuntimeException("Refund network connection reset"));

        IO<String> shippingAction = IO.failed(new RuntimeException("UPS failure"));
        IO<Void> shippingCancel = IO.of(null);

        // 1. Run Step 1 (Payment succeeds)
        process.runStep("tx-203", "payment", cardCharge, cardRefund, "Execute payment", now).unsafeRunSync();

        // 2. Run Step 2 (Shipping fails) -> triggers payment refund which fails
        Either<String, String> shippingRes = process.runStep("tx-203", "shipping", shippingAction, shippingCancel, "Create shipping labels", now.plusSeconds(10)).unsafeRunSync();
        assertTrue(shippingRes.isLeft());

        // Verify terminal state becomes FAILED (indicating inconsistent distributed compensation failure)
        CompensationLedger<String, String> ledger = repo.find("tx-203").unsafeRunSync().orElseThrow();
        assertTrue(ledger.isTerminal());

        TestCompProjector projector = new TestCompProjector();
        ledger.projectState(projector);
        assertEquals(CompensationLedger.Status.FAILED, projector.status);
        assertEquals(3, projector.history.size()); // payment success, rollback trigger, compensation failure
        assertEquals(SagaStep.Type.COMPENSATION_FAILURE, projector.history.get(2).type());

        // Verify events
        List<CompensableEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(3, events.size());
        assertTrue(events.get(0) instanceof SagaStepSucceeded);
        assertTrue(events.get(1) instanceof SagaRollbackTriggered);
        assertTrue(events.get(2) instanceof SagaCompensatedFailed);
    }
}
