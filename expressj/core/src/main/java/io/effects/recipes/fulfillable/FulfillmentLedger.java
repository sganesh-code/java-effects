package io.effects.recipes.fulfillable;

import io.effects.recipes.fulfillable.models.*;

import io.effects.core.Either;
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing the current fulfillment status
 * and history of steps.
 * It is an Aggregate Root that completely owns state progressions and produces FulfillmentEvent occurrences.
 */
public final class FulfillmentLedger<ID, Q> {
    public enum Status { INITIAL, ALLOCATING, PACKAGING, DISPATCHED, COMPLETED }

    private final ID fulfillmentId;
    private Status status = Status.INITIAL;
    private final List<FulfillmentStep<Q>> history = new ArrayList<>();

    public FulfillmentLedger(ID fulfillmentId) {
        this.fulfillmentId = Objects.requireNonNull(fulfillmentId);
    }

    public synchronized ID fulfillmentId() { return fulfillmentId; }
    public synchronized Status status() { return status; }
    public synchronized List<FulfillmentStep<Q>> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }

    public synchronized boolean isTerminal() {
        return status == Status.COMPLETED;
    }

    /**
     * Records a step and transitions state internally.
     */
    private synchronized void recordStep(FulfillmentStep<Q> step, Status nextStatus) {
        Objects.requireNonNull(step);
        Objects.requireNonNull(nextStatus);

        if (isTerminal()) {
            throw new IllegalStateException("Cannot register a step on a terminal fulfillment ledger: " + fulfillmentId);
        }

        this.history.add(step);
        this.status = nextStatus;
    }

    /**
     * Behavioral Factory: Creates a new fulfillment ledger.
     */
    public static <ID, Q> FulfillmentLedger<ID, Q> initiate(ID fulfillmentId) {
        return new FulfillmentLedger<>(fulfillmentId);
    }

    /**
     * Behavioral Transition: Allocates items.
     */
    public synchronized Either<String, FulfillmentEvent<ID, Q>> allocate(
        String actorId, 
        Q detail, 
        String comment, 
        FulfillableRequest<ID, Q> request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.INITIAL && status != Status.ALLOCATING) {
            return Either.left("Cannot allocate in current status: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateAllocation(this, detail, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        FulfillmentStep<Q> step = new FulfillmentStep<>(
            UUID.randomUUID().toString(),
            actorId,
            FulfillmentStep.Type.ALLOCATE,
            detail,
            comment,
            now
        );
        recordStep(step, Status.ALLOCATING);

        FulfillmentEvent<ID, Q> event = new FulfillmentAllocated<>(fulfillmentId, detail, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Packages allocated items.
     */
    public synchronized Either<String, FulfillmentEvent<ID, Q>> packageItems(
        String actorId, 
        Q detail, 
        String comment, 
        FulfillableRequest<ID, Q> request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.ALLOCATING && status != Status.PACKAGING) {
            return Either.left("Cannot package in current status: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluatePackaging(this, detail, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        FulfillmentStep<Q> step = new FulfillmentStep<>(
            UUID.randomUUID().toString(),
            actorId,
            FulfillmentStep.Type.PACKAGE,
            detail,
            comment,
            now
        );
        recordStep(step, Status.PACKAGING);

        return Either.right(null); // No public event required for packaging step
    }

    /**
     * Behavioral Transition: Dispatches packaged items.
     */
    public synchronized Either<String, FulfillmentEvent<ID, Q>> dispatch(
        String actorId, 
        String comment, 
        FulfillableRequest<ID, Q> request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status == Status.DISPATCHED) {
            return Either.right(null); // Idempotent success
        }
        if (status != Status.PACKAGING) {
            return Either.left("Cannot dispatch in current status: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateDispatch(this, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        Q detail = history.stream()
            .filter(s -> s.type() == FulfillmentStep.Type.PACKAGE || s.type() == FulfillmentStep.Type.ALLOCATE)
            .map(FulfillmentStep::detail)
            .reduce((first, second) -> second)
            .orElse(null);

        FulfillmentStep<Q> step = new FulfillmentStep<>(
            UUID.randomUUID().toString(),
            actorId,
            FulfillmentStep.Type.DISPATCH,
            detail,
            comment,
            now
        );
        recordStep(step, Status.DISPATCHED);

        FulfillmentEvent<ID, Q> event = new FulfillmentDispatched<>(fulfillmentId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Completes fulfillment/delivery.
     */
    public synchronized Either<String, FulfillmentEvent<ID, Q>> complete(
        String actorId, 
        String comment, 
        FulfillableRequest<ID, Q> request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status == Status.COMPLETED) {
            return Either.right(null); // Idempotent success
        }
        if (status != Status.DISPATCHED) {
            return Either.left("Cannot complete delivery in current status: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateCompletion(this, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        Q detail = history.stream()
            .filter(s -> s.type() == FulfillmentStep.Type.DISPATCH || s.type() == FulfillmentStep.Type.PACKAGE)
            .map(FulfillmentStep::detail)
            .reduce((first, second) -> second)
            .orElse(null);

        FulfillmentStep<Q> step = new FulfillmentStep<>(
            UUID.randomUUID().toString(),
            actorId,
            FulfillmentStep.Type.COMPLETE,
            detail,
            comment,
            now
        );
        recordStep(step, Status.COMPLETED);

        FulfillmentEvent<ID, Q> event = new FulfillmentCompleted<>(fulfillmentId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Releases allocated or packaged items back to inventory.
     */
    public synchronized Either<String, FulfillmentEvent<ID, Q>> release(
        String actorId, 
        Q detail, 
        String comment, 
        FulfillableRequest<ID, Q> request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.ALLOCATING && status != Status.PACKAGING) {
            return Either.left("Cannot release in current status: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Status> eitherValid = request.evaluateRelease(this, detail, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        Status nextStatus = eitherValid.getRight();

        FulfillmentStep<Q> step = new FulfillmentStep<>(
            UUID.randomUUID().toString(),
            actorId,
            FulfillmentStep.Type.RELEASE,
            detail,
            comment,
            now
        );

        recordStep(step, nextStatus);

        FulfillmentEvent<ID, Q> event = new FulfillmentReleased<>(fulfillmentId, detail, now);
        return Either.right(event);
    }
}