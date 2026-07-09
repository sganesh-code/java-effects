package io.effects.recipes;

import io.effects.IO;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An Object-Oriented "Recipe" representing a Circuit Breaker.
 * Protects failing downstream resources by transitioning states (CLOSED, OPEN, HALF_OPEN)
 * and preventing unnecessary executions when the breaker is open.
 * Leverages the pure functional IO core to implement a transparent protection wrapper.
 */
public final class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final Duration resetTimeout;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    public CircuitBreaker(int failureThreshold, Duration resetTimeout) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.resetTimeout = Objects.requireNonNull(resetTimeout);
    }

    /**
     * Retrieves the current state of the Circuit Breaker.
     */
    public State getState() {
        return state.get();
    }

    /**
     * Wraps an IO computation to protect it with Circuit Breaker semantics.
     */
    public <A> IO<A> protect(IO<A> task) {
        Objects.requireNonNull(task);
        return IO.delay(this::checkAndUpdateState).flatMap(currentState -> {
            if (currentState == State.OPEN) {
                return IO.failed(new RuntimeException("Circuit breaker is OPEN"));
            }

            return task.attempt().flatMap(result -> result.fold(
                error -> IO.delay(() -> {
                    onFailure();
                    return null;
                }).flatMap(v -> IO.failed(error)),
                value -> IO.delay(() -> {
                    onSuccess();
                    return value;
                })
            ));
        });
    }

    private State checkAndUpdateState() {
        State current = state.get();
        if (current == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastFailureTime.get();
            if (elapsed >= resetTimeout.toMillis()) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    return State.HALF_OPEN;
                }
            }
        }
        return state.get();
    }

    private void onSuccess() {
        state.set(State.CLOSED);
        failureCount.set(0);
    }

    private void onFailure() {
        long now = System.currentTimeMillis();
        lastFailureTime.set(now);

        if (state.get() == State.HALF_OPEN) {
            state.set(State.OPEN);
        } else {
            int currentFailures = failureCount.incrementAndGet();
            if (currentFailures >= failureThreshold) {
                state.set(State.OPEN);
            }
        }
    }
}
