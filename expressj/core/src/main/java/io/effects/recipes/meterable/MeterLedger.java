package io.effects.recipes.meterable;

import io.effects.Either;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing an account's metered usage consumption.
 * It is an Aggregate Root that encapsulates lifecycle transitions and executes double-dispatch.
 */
public final class MeterLedger<ID, U> {
    public enum Status { INITIAL, ACTIVE, FINALIZED }

    private final ID accountId;
    private Status status = Status.INITIAL;
    private final List<UsageStep<U>> history = new ArrayList<>();

    public MeterLedger(ID accountId) {
        this.accountId = Objects.requireNonNull(accountId);
    }

    public synchronized ID accountId() {
        return accountId;
    }

    public synchronized Status status() {
        return status;
    }

    public synchronized List<UsageStep<U>> history() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public synchronized boolean isTerminal() {
        return status == Status.FINALIZED;
    }

    /**
     * Behavioral Transition: Starts/Activates the usage billing meter.
     */
    public synchronized Either<String, Void> start(Instant now) {
        Objects.requireNonNull(now);
        if (status != Status.INITIAL) {
            return Either.left("Cannot start: meter is already active or finalized (current status: " + status + ")");
        }
        this.status = Status.ACTIVE;
        return Either.right(null);
    }

    /**
     * Behavioral Transition: Records a new consumption usage tick, checking constraints via double-dispatch.
     */
    public synchronized Either<String, UsageStep<U>> recordUsage(
        U metric, 
        MeterableRequest<ID, U, ?> request, 
        Instant now
    ) {
        Objects.requireNonNull(metric);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.ACTIVE) {
            return Either.left("Cannot record usage: billing meter is not ACTIVE (current status: " + status + ")");
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateUsage(this, metric, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        UsageStep<U> step = new UsageStep<>(UUID.randomUUID().toString(), metric, now);
        history.add(step);
        return Either.right(step);
    }

    /**
     * Behavioral Transition: Rates the recorded usage history, finalizes the billing cycle, and resets state.
     */
    public synchronized <R> Either<String, R> rate(
        MeterableRequest<ID, U, R> request, 
        Instant now
    ) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.ACTIVE) {
            return Either.left("Cannot rate: billing meter is not ACTIVE (current status: " + status + ")");
        }

        // Domain evaluation (double dispatch)
        Either<String, R> eitherRating = request.evaluateRating(this, now);
        if (eitherRating.isLeft()) {
            return Either.left(eitherRating.getLeft());
        }

        this.status = Status.FINALIZED;
        return Either.right(eitherRating.getRight());
    }

    /**
     * Clears history to optimize memory footprint after billing finalization.
     */
    public synchronized void compact() {
        this.history.clear();
    }
}