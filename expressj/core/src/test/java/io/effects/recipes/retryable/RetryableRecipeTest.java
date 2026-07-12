package io.effects.recipes.retryable;

import io.effects.recipes.retryable.models.*;
import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryableRecipeTest {

    static record PaymentGatewayRetry(int maxAttempts, long backoffDelay) implements RetryableRequest<String, String> {
        @Override
        public boolean isTransientFailure(Throwable error) {
            // IllegalArgumentException is considered permanent/fatal, others are transient
            return !(error instanceof IllegalArgumentException);
        }

        @Override
        public long calculateBackoffMillis(int currentAttempt, Instant now) {
            return backoffDelay;
        }
    }

    @Test
    void testInstantSuccessPath() {
        InMemoryStateRepository<String, RetryLedger<String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<RetryableEvent<String>> publisher = new InMemoryEventPublisher<>();
        RetryableProcess<String, String> process = new RetryableProcess<>(repo, publisher, new NoOpTelemetryPort());

        PaymentGatewayRetry policy = new PaymentGatewayRetry(3, 10);
        process.register("op-001", policy).unsafeRunSync();

        IO<String> successAction = IO.of("Success payload");
        Instant now = Instant.parse("2026-07-12T12:00:00Z");

        Either<String, String> result = process.execute("op-001", successAction, "Charge client", now).unsafeRunSync();
        assertTrue(result.isRight());
        assertEquals("Success payload", result.getRight());

        RetryLedger<String, String> ledger = repo.find("op-001").unsafeRunSync().orElseThrow();
        assertEquals(RetryLedger.Status.SUCCEEDED, ledger.status());
        assertEquals(1, ledger.attempts());
        assertTrue(ledger.isTerminal());

        // Verify history steps count (EXECUTE_ATTEMPT, SUCCESS)
        List<RetryStep<String>> history = ledger.history();
        assertEquals(2, history.size());
        assertEquals(RetryStep.Type.EXECUTE_ATTEMPT, history.get(0).type());
        assertEquals(RetryStep.Type.SUCCESS, history.get(1).type());

        // Verify published events
        List<RetryableEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof ExecutionSucceeded);
    }

    @Test
    void testBrittleOperationSucceedsOnSecondAttempt() {
        InMemoryStateRepository<String, RetryLedger<String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<RetryableEvent<String>> publisher = new InMemoryEventPublisher<>();
        RetryableProcess<String, String> process = new RetryableProcess<>(repo, publisher, new NoOpTelemetryPort());

        PaymentGatewayRetry policy = new PaymentGatewayRetry(3, 10);
        process.register("op-002", policy).unsafeRunSync();

        AtomicInteger callCounter = new AtomicInteger(0);
        IO<String> brittleAction = IO.delay(() -> {
            if (callCounter.getAndIncrement() == 0) {
                throw new RuntimeException("Transient timeout error");
            }
            return "Ok";
        });

        Instant now = Instant.parse("2026-07-12T12:05:00Z");

        Either<String, String> result = process.execute("op-002", brittleAction, "Sync inventory", now).unsafeRunSync();
        assertTrue(result.isRight());
        assertEquals("Ok", result.getRight());

        RetryLedger<String, String> ledger = repo.find("op-002").unsafeRunSync().orElseThrow();
        assertEquals(RetryLedger.Status.SUCCEEDED, ledger.status());
        assertEquals(2, ledger.attempts());

        // Verify history contains retry steps: EXECUTE_ATTEMPT, ATTEMPT_FAILURE, SCHEDULE_RETRY, EXECUTE_ATTEMPT, SUCCESS
        List<RetryStep<String>> history = ledger.history();
        assertEquals(5, history.size());
        assertEquals(RetryStep.Type.EXECUTE_ATTEMPT, history.get(0).type());
        assertEquals(RetryStep.Type.ATTEMPT_FAILURE, history.get(1).type());
        assertEquals(RetryStep.Type.SCHEDULE_RETRY, history.get(2).type());
        assertEquals(RetryStep.Type.EXECUTE_ATTEMPT, history.get(3).type());
        assertEquals(RetryStep.Type.SUCCESS, history.get(4).type());

        // Verify published events (RetryScheduled, ExecutionSucceeded)
        List<RetryableEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof RetryScheduled);
        assertTrue(events.get(1) instanceof ExecutionSucceeded);
    }

    @Test
    void testPermanentFailureExceedsLimits() {
        InMemoryStateRepository<String, RetryLedger<String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<RetryableEvent<String>> publisher = new InMemoryEventPublisher<>();
        RetryableProcess<String, String> process = new RetryableProcess<>(repo, publisher, new NoOpTelemetryPort());

        PaymentGatewayRetry policy = new PaymentGatewayRetry(3, 10);
        process.register("op-003", policy).unsafeRunSync();

        IO<String> failingAction = IO.delay(() -> {
            throw new RuntimeException("Brittle service down permanently");
        });

        Instant now = Instant.parse("2026-07-12T12:10:00Z");

        Either<String, String> result = process.execute("op-003", failingAction, "Charge card", now).unsafeRunSync();
        assertTrue(result.isLeft());
        assertTrue(result.getLeft().contains("Execution abandoned after 3 attempts"));

        RetryLedger<String, String> ledger = repo.find("op-003").unsafeRunSync().orElseThrow();
        assertEquals(RetryLedger.Status.FAILED, ledger.status());
        assertEquals(3, ledger.attempts());
        assertTrue(ledger.isTerminal());

        // Verify final event is ExecutionAbandoned
        List<RetryableEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(3, events.size()); // 2 retries scheduled, 1 final abandon
        assertTrue(events.get(0) instanceof RetryScheduled);
        assertTrue(events.get(1) instanceof RetryScheduled);
        assertTrue(events.get(2) instanceof ExecutionAbandoned);
    }

    @Test
    void testFatalFailureAbandonsImmediately() {
        InMemoryStateRepository<String, RetryLedger<String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<RetryableEvent<String>> publisher = new InMemoryEventPublisher<>();
        RetryableProcess<String, String> process = new RetryableProcess<>(repo, publisher, new NoOpTelemetryPort());

        PaymentGatewayRetry policy = new PaymentGatewayRetry(3, 10);
        process.register("op-004", policy).unsafeRunSync();

        IO<String> fatalAction = IO.delay(() -> {
            throw new IllegalArgumentException("Fatal invalid client ID format");
        });

        Instant now = Instant.parse("2026-07-12T12:15:00Z");

        Either<String, String> result = process.execute("op-004", fatalAction, "Validate account", now).unsafeRunSync();
        assertTrue(result.isLeft());
        assertTrue(result.getLeft().contains("Execution abandoned after 1 attempts"));

        RetryLedger<String, String> ledger = repo.find("op-004").unsafeRunSync().orElseThrow();
        assertEquals(RetryLedger.Status.FAILED, ledger.status());
        assertEquals(1, ledger.attempts());

        List<RetryableEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(1, events.size());
        assertTrue(events.get(0) instanceof ExecutionAbandoned);
    }
}
