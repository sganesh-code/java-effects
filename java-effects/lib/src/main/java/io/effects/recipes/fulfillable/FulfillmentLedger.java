package io.effects.recipes.fulfillable;

import io.effects.Either;
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing the current fulfillment status,
 * allocated quantity, packaged quantity, total requested capacity, and history of steps.
 * It is an Aggregate Root that completely owns state progressions and produces FulfillmentEvent occurrences.
 */
public final class FulfillmentLedger {
    public enum Status { INITIAL, ALLOCATING, PACKAGING, DISPATCHED, COMPLETED }

    private final String fulfillmentId;
    private final int totalQuantity;
    private Status status = Status.INITIAL;
    private int allocatedQuantity = 0;
    private int packagedQuantity = 0;
    private final List<FulfillmentStep> history = new ArrayList<>();

    public FulfillmentLedger(String fulfillmentId, int totalQuantity) {
        this.fulfillmentId = Objects.requireNonNull(fulfillmentId);
        if (totalQuantity <= 0) {
            throw new IllegalArgumentException("Total quantity must be positive");
        }
        this.totalQuantity = totalQuantity;
    }

    public synchronized String fulfillmentId() { return fulfillmentId; }
    public synchronized int totalQuantity() { return totalQuantity; }
    public synchronized Status status() { return status; }
    public synchronized int allocatedQuantity() { return allocatedQuantity; }
    public synchronized int packagedQuantity() { return packagedQuantity; }
    public synchronized List<FulfillmentStep> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }

    public synchronized boolean isTerminal() {
        return status == Status.COMPLETED;
    }

    /**
     * Records a step and transitions state internally.
     */
    private synchronized void recordStep(
        FulfillmentStep step, 
        Status nextStatus, 
        int allocDiff, 
        int packageDiff
    ) {
        Objects.requireNonNull(step);
        Objects.requireNonNull(nextStatus);

        if (isTerminal()) {
            throw new IllegalStateException("Cannot record a step on a terminal fulfillment ledger: " + fulfillmentId);
        }

        this.history.add(step);
        this.status = nextStatus;
        this.allocatedQuantity += allocDiff;
        this.packagedQuantity += packageDiff;
    }

    /**
     * Behavioral Factory: Creates a new fulfillment ledger.
     */
    public static FulfillmentLedger initiate(String fulfillmentId, int totalQuantity) {
        return new FulfillmentLedger(fulfillmentId, totalQuantity);
    }

    /**
     * Behavioral Transition: Allocates items.
     */
    public synchronized Either<String, FulfillmentEvent> allocate(
        String actorId, 
        int quantity, 
        String comment, 
        FulfillableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.INITIAL && status != Status.ALLOCATING) {
            return Either.left("Cannot allocate in current status: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateAllocation(this, quantity, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        FulfillmentStep step = new FulfillmentStep(
            UUID.randomUUID().toString(),
            actorId,
            FulfillmentStep.Type.ALLOCATE,
            quantity,
            comment,
            now
        );
        recordStep(step, Status.ALLOCATING, quantity, 0);

        FulfillmentEvent event = new FulfillmentAllocated(fulfillmentId, quantity, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Packages allocated items.
     */
    public synchronized Either<String, FulfillmentEvent> packageItems(
        String actorId, 
        int quantity, 
        String comment, 
        FulfillableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.ALLOCATING && status != Status.PACKAGING) {
            return Either.left("Cannot package in current status: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluatePackaging(this, quantity, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        FulfillmentStep step = new FulfillmentStep(
            UUID.randomUUID().toString(),
            actorId,
            FulfillmentStep.Type.PACKAGE,
            quantity,
            comment,
            now
        );
        recordStep(step, Status.PACKAGING, 0, quantity);

        return Either.right(null); // No public event required for packaging step
    }

    /**
     * Behavioral Transition: Dispatches packaged items.
     */
    public synchronized Either<String, FulfillmentEvent> dispatch(
        String actorId, 
        String comment, 
        FulfillableRequest request, 
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

        FulfillmentStep step = new FulfillmentStep(
            UUID.randomUUID().toString(),
            actorId,
            FulfillmentStep.Type.DISPATCH,
            packagedQuantity,
            comment,
            now
        );
        recordStep(step, Status.DISPATCHED, 0, 0);

        FulfillmentEvent event = new FulfillmentDispatched(fulfillmentId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Completes fulfillment/delivery.
     */
    public synchronized Either<String, FulfillmentEvent> complete(
        String actorId, 
        String comment, 
        FulfillableRequest request, 
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

        FulfillmentStep step = new FulfillmentStep(
            UUID.randomUUID().toString(),
            actorId,
            FulfillmentStep.Type.COMPLETE,
            packagedQuantity,
            comment,
            now
        );
        recordStep(step, Status.COMPLETED, 0, 0);

        FulfillmentEvent event = new FulfillmentCompleted(fulfillmentId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Releases allocated or packaged items back to inventory.
     */
    public synchronized Either<String, FulfillmentEvent> release(
        String actorId, 
        int quantity, 
        String comment, 
        FulfillableRequest request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.ALLOCATING && status != Status.PACKAGING) {
            return Either.left("Cannot release in current status: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateRelease(this, quantity, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        FulfillmentStep step = new FulfillmentStep(
            UUID.randomUUID().toString(),
            actorId,
            FulfillmentStep.Type.RELEASE,
            quantity,
            comment,
            now
        );

        // Deduct both allocated and packaged quantities as appropriate
        int allocDeduct = Math.min(quantity, allocatedQuantity);
        int packDeduct = Math.min(quantity, packagedQuantity);

        Status nextStatus = (allocatedQuantity - allocDeduct) == 0 
            ? Status.INITIAL 
            : status;

        recordStep(step, nextStatus, -allocDeduct, -packDeduct);

        FulfillmentEvent event = new FulfillmentReleased(fulfillmentId, quantity, now);
        return Either.right(event);
    }
}
