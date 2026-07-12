package io.effects.recipes.throttlable;

import io.effects.recipes.throttlable.models.*;
import io.effects.core.Either;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing an adaptive Token Bucket rate limiter.
 *
 * Adhering strictly to pure OOP principles:
 * - It contains **zero passive getters**.
 * - It exposes its internal state strictly via a visitor projector pattern.
 * - It owns state transitions and enforces Token Bucket refills/consumptions.
 */
public final class TokenBucketLedger<ID, C> {
    private final ID actorId;
    private double currentTokens = -1.0; // -1.0 signifies uninitialized (full capacity)
    private Instant lastRefillTime = null;
    private final List<ThrottleStep<C>> history = new ArrayList<>();

    public TokenBucketLedger(ID actorId) {
        this.actorId = Objects.requireNonNull(actorId);
    }

    /**
     * Pure OOP State Projection: Projects state strictly onto a visitor projector.
     */
    public synchronized void projectState(ThrottlableProjector<ID, C> projector) {
        Objects.requireNonNull(projector);
        projector.project(
            actorId,
            currentTokens,
            lastRefillTime,
            Collections.unmodifiableList(new ArrayList<>(history))
        );
    }

    /**
     * Internal/Behavioral helper: Calculates and applies token refills based on elapsed time.
     */
    private synchronized void refill(double maxCapacity, double refillRatePerMillis, Instant now, C comment) {
        if (currentTokens < 0.0 || lastRefillTime == null) {
            this.currentTokens = maxCapacity;
            this.lastRefillTime = now;
            return;
        }

        long elapsedMillis = Math.max(0L, now.toEpochMilli() - lastRefillTime.toEpochMilli());
        if (elapsedMillis > 0) {
            double refilled = elapsedMillis * refillRatePerMillis;
            double nextTokens = Math.min(maxCapacity, currentTokens + refilled);
            double added = nextTokens - currentTokens;

            if (added > 0.0) {
                this.currentTokens = nextTokens;
                this.lastRefillTime = now;

                ThrottleStep<C> step = new ThrottleStep<>(
                    UUID.randomUUID().toString(),
                    added,
                    ThrottleStep.Type.REFILL,
                    comment,
                    now
                );
                this.history.add(step);
            }
        }
    }

    /**
     * Behavioral Factory: Creates a new token bucket ledger.
     */
    public static <ID, C> TokenBucketLedger<ID, C> initiate(ID actorId) {
        return new TokenBucketLedger<>(actorId);
    }

    /**
     * Behavioral Transition: Refills the bucket and consumes tokens if available.
     * If capacity is insufficient, it records a throttle rejection.
     */
    public synchronized Either<String, ThrottlableEvent<ID>> consume(
        double requestedTokens, 
        C comment, 
        ThrottlableRequest<ID, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (requestedTokens <= 0) {
            return Either.left("Cannot consume non-positive tokens: " + requestedTokens);
        }

        // Apply refills first
        refill(request.maxCapacity(), request.refillRatePerMillis(), now, comment);

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateConsume(this, requestedTokens, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        if (currentTokens >= requestedTokens) {
            currentTokens -= requestedTokens;

            ThrottleStep<C> step = new ThrottleStep<>(
                UUID.randomUUID().toString(),
                requestedTokens,
                ThrottleStep.Type.CONSUME_SUCCESS,
                comment,
                now
            );
            this.history.add(step);

            ThrottlableEvent<ID> event = new TokensConsumed<>(actorId, requestedTokens, currentTokens, now);
            return Either.right(event);
        } else {
            ThrottleStep<C> step = new ThrottleStep<>(
                UUID.randomUUID().toString(),
                requestedTokens,
                ThrottleStep.Type.THROTTLE_REJECT,
                comment,
                now
            );
            this.history.add(step);

            ThrottlableEvent<ID> event = new RateThrottled<>(actorId, requestedTokens, currentTokens, now);
            return Either.right(event);
        }
    }
}
