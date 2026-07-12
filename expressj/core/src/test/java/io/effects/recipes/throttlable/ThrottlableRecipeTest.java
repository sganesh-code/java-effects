package io.effects.recipes.throttlable;

import io.effects.recipes.throttlable.models.*;
import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThrottlableRecipeTest {

    static class TestThrottlableProjector implements ThrottlableProjector<String, String> {
        String actorId;
        double currentTokens;
        Instant lastRefillTime;
        List<ThrottleStep<String>> history;

        @Override
        public void project(
            String actorId, 
            double currentTokens, 
            Instant lastRefillTime, 
            List<ThrottleStep<String>> history
        ) {
            this.actorId = actorId;
            this.currentTokens = currentTokens;
            this.lastRefillTime = lastRefillTime;
            this.history = history;
        }
    }

    static record ClientRateLimit(double maxCapacity, double refillRatePerMillis) 
        implements ThrottlableRequest<String, String> {

        @Override
        public Either<String, Void> evaluateConsume(TokenBucketLedger<String, String> ledger, double requestedTokens, Instant now) {
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateRefill(TokenBucketLedger<String, String> ledger, double refilledTokens, Instant now) {
            return Either.right(null);
        }
    }

    @Test
    void testInitialCapacityAndTokenConsumption() {
        InMemoryStateRepository<String, TokenBucketLedger<String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<ThrottlableEvent<String>> publisher = new InMemoryEventPublisher<>();
        ThrottlableProcess<String, String> process = new ThrottlableProcess<>(repo, publisher, new NoOpTelemetryPort());

        ClientRateLimit limit = new ClientRateLimit(10.0, 0.01); // 10 tokens capacity, 10 per sec refill
        process.register("client-api-key", limit).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T17:00:00Z");

        // 1. First consumption (uninitialized -> refills to max 10.0 first, then consumes 4.0)
        Either<String, TokenBucketLedger<String, String>> res1 = 
            process.consume("client-api-key", 4.0, "API request 1", now).unsafeRunSync();
        assertTrue(res1.isRight());

        TokenBucketLedger<String, String> ledger = res1.getRight();
        TestThrottlableProjector projector = new TestThrottlableProjector();
        ledger.projectState(projector);
        assertEquals("client-api-key", projector.actorId);
        assertEquals(6.0, projector.currentTokens);
        assertEquals(now, projector.lastRefillTime);

        // 2. Consume another 5.0 tokens -> should succeed
        Either<String, TokenBucketLedger<String, String>> res2 = 
            process.consume("client-api-key", 5.0, "API request 2", now).unsafeRunSync();
        assertTrue(res2.isRight());

        TestThrottlableProjector projector2 = new TestThrottlableProjector();
        res2.getRight().projectState(projector2);
        assertEquals(1.0, projector2.currentTokens);

        // Verify published events (2 TokensConsumed events)
        List<ThrottlableEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof TokensConsumed);
        assertTrue(events.get(1) instanceof TokensConsumed);

        TokensConsumed<String> tc = (TokensConsumed<String>) events.get(1);
        assertEquals(5.0, tc.tokensConsumed());
        assertEquals(1.0, tc.remainingCapacity());
    }

    @Test
    void testRateThrottlingRejection() {
        InMemoryStateRepository<String, TokenBucketLedger<String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<ThrottlableEvent<String>> publisher = new InMemoryEventPublisher<>();
        ThrottlableProcess<String, String> process = new ThrottlableProcess<>(repo, publisher, new NoOpTelemetryPort());

        ClientRateLimit limit = new ClientRateLimit(10.0, 0.01);
        process.register("client-api-key-2", limit).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T17:05:00Z");

        // Consume 9.0 tokens (leaves 1.0)
        process.consume("client-api-key-2", 9.0, "API Request", now).unsafeRunSync();

        // Try consuming 3.0 tokens -> should fail/throttle (only 1.0 available)
        Either<String, TokenBucketLedger<String, String>> rejectRes = 
            process.consume("client-api-key-2", 3.0, "Bursty API Request", now).unsafeRunSync();
        assertTrue(rejectRes.isRight()); // Transitions successfully to throttle state

        TokenBucketLedger<String, String> ledger = rejectRes.getRight();
        TestThrottlableProjector projector = new TestThrottlableProjector();
        ledger.projectState(projector);
        assertEquals(1.0, projector.currentTokens); // Capacity remains unchanged at 1.0

        // Verify RateThrottled event was published
        List<ThrottlableEvent<String>> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof TokensConsumed);
        assertTrue(events.get(1) instanceof RateThrottled);

        RateThrottled<String> rt = (RateThrottled<String>) events.get(1);
        assertEquals(3.0, rt.requestedTokens());
        assertEquals(1.0, rt.availableTokens());
    }

    @Test
    void testAdaptiveTokenRefillsOverTime() {
        InMemoryStateRepository<String, TokenBucketLedger<String, String>> repo = new InMemoryStateRepository<>();
        InMemoryEventPublisher<ThrottlableEvent<String>> publisher = new InMemoryEventPublisher<>();
        ThrottlableProcess<String, String> process = new ThrottlableProcess<>(repo, publisher, new NoOpTelemetryPort());

        ClientRateLimit limit = new ClientRateLimit(10.0, 0.01); // 0.01 tokens refilled per millis (10 per second)
        process.register("client-api-key-3", limit).unsafeRunSync();

        Instant now = Instant.parse("2026-07-12T17:10:00Z");

        // Consume 9.0 tokens (leaves 1.0)
        process.consume("client-api-key-3", 9.0, "Initial consume", now).unsafeRunSync();

        // Advance time by 500ms (refills: 500ms * 0.01 tokens/ms = 5.0 tokens)
        // Total capacity should refill to: 1.0 + 5.0 = 6.0 tokens
        Instant advancedTime = now.plusMillis(500);

        // Try consuming 4.0 tokens -> should succeed now because of the refill!
        Either<String, TokenBucketLedger<String, String>> refillRes = 
            process.consume("client-api-key-3", 4.0, "Post-refill consume", advancedTime).unsafeRunSync();
        assertTrue(refillRes.isRight());

        TokenBucketLedger<String, String> ledger = refillRes.getRight();
        TestThrottlableProjector projector = new TestThrottlableProjector();
        ledger.projectState(projector);
        assertEquals(2.0, projector.currentTokens); // 6.0 capacity - 4.0 consumed = 2.0 remaining

        // Verify history contains a REFILL step
        assertEquals(3, projector.history.size()); // consume 9, refill 5, consume 4
        assertEquals(ThrottleStep.Type.CONSUME_SUCCESS, projector.history.get(0).type());
        assertEquals(ThrottleStep.Type.REFILL, projector.history.get(1).type());
        assertEquals(5.0, projector.history.get(1).tokens());
        assertEquals(ThrottleStep.Type.CONSUME_SUCCESS, projector.history.get(2).type());
    }
}
