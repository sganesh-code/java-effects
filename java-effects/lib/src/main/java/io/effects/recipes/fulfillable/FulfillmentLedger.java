package io.effects.recipes.fulfillable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A non-anemic, thread-safe domain state ledger representing the current fulfillment status,
 * allocated quantity, packaged quantity, total requested capacity, and history of steps.
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
     * Records a step and transitions state.
     * Enforces that no further steps can be appended if the record has reached a terminal state.
     */
    public synchronized void recordStep(
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
}
