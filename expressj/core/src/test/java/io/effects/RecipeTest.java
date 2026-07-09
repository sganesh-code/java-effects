package io.effects;

import io.effects.recipes.CircuitBreaker;
import io.effects.recipes.Saga;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RecipeTest {

    @Test
    void testSagaSuccessfulTransaction() {
        List<String> auditLog = new ArrayList<>();

        IO<String> step1Action = IO.delay(() -> {
            auditLog.add("Step1 action");
            return "Item1";
        });
        IO<String> step2Action = IO.delay(() -> {
            auditLog.add("Step2 action");
            return "Item2";
        });

        IO<String> sagaIO = Saga.create()
                .addStep(step1Action, item -> IO.delay(() -> {
                    auditLog.add("Rollback " + item);
                    return null;
                }))
                .addStep(step2Action, item -> IO.delay(() -> {
                    auditLog.add("Rollback " + item);
                    return null;
                }))
                .map(item -> "Saga Succeeded with: " + item)
                .transact();

        String result = sagaIO.unsafeRunSync();

        assertEquals("Saga Succeeded with: Item2", result);
        assertEquals(2, auditLog.size());
        assertEquals("Step1 action", auditLog.get(0));
        assertEquals("Step2 action", auditLog.get(1));
    }

    @Test
    void testSagaFailedTransactionRollback() {
        List<String> auditLog = new ArrayList<>();

        IO<String> step1Action = IO.delay(() -> {
            auditLog.add("Step1 action");
            return "Item1";
        });
        IO<String> step2Action = IO.delay(() -> {
            auditLog.add("Step2 action");
            return "Item2";
        });
        IO<String> failingStep = IO.delay(() -> {
            auditLog.add("Step3 failing");
            throw new RuntimeException("DB Connection Lost");
        });

        IO<String> sagaIO = Saga.create()
                .addStep(step1Action, item -> IO.delay(() -> {
                    auditLog.add("Rollback " + item);
                    return null;
                }))
                .addStep(step2Action, item -> IO.delay(() -> {
                    auditLog.add("Rollback " + item);
                    return null;
                }))
                .addStep(failingStep, item -> IO.delay(() -> {
                    auditLog.add("Rollback step 3");
                    return null;
                }))
                .transact();

        RuntimeException exception = assertThrows(RuntimeException.class, sagaIO::unsafeRunSync);
        assertTrue(exception.getMessage().contains("DB Connection Lost"));

        // Verify Step 1 and Step 2 completed, step 3 failed, and step 2 and step 1 rolled back in reverse order.
        assertEquals(5, auditLog.size());
        assertEquals("Step1 action", auditLog.get(0));
        assertEquals("Step2 action", auditLog.get(1));
        assertEquals("Step3 failing", auditLog.get(2));
        assertEquals("Rollback Item2", auditLog.get(3));
        assertEquals("Rollback Item1", auditLog.get(4));
    }

    @Test
    void testCircuitBreakerFlow() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(2, Duration.ofMillis(100));
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());

        AtomicInteger runCount = new AtomicInteger(0);
        IO<Integer> successfulTask = IO.delay(() -> {
            runCount.incrementAndGet();
            return 42;
        });

        // Test success does not trip the breaker
        assertEquals(42, cb.protect(successfulTask).unsafeRunSync());
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertEquals(1, runCount.get());

        IO<Integer> failingTask = IO.delay(() -> {
            throw new RuntimeException("Backend failure");
        });

        // First failure
        assertThrows(RuntimeException.class, () -> cb.protect(failingTask).unsafeRunSync());
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());

        // Second failure -> Trips breaker to OPEN
        assertThrows(RuntimeException.class, () -> cb.protect(failingTask).unsafeRunSync());
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());

        // Fast failing while OPEN (should throw immediately without executing task)
        AtomicInteger taskExecCount = new AtomicInteger(0);
        IO<Integer> dummyTask = IO.delay(() -> {
            taskExecCount.incrementAndGet();
            return 100;
        });

        RuntimeException openException = assertThrows(RuntimeException.class, () -> cb.protect(dummyTask).unsafeRunSync());
        assertTrue(openException.getMessage().contains("Circuit breaker is OPEN"));
        assertEquals(0, taskExecCount.get()); // Task was not run

        // Wait for reset timeout
        Thread.sleep(150);

        // State updates to HALF_OPEN upon checking
        // Execute a success task to close the breaker
        assertEquals(42, cb.protect(successfulTask).unsafeRunSync());
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }
}
