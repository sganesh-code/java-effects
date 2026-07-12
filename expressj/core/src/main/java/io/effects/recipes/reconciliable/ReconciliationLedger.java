package io.effects.recipes.reconciliable;

import io.effects.recipes.reconciliable.models.*;
import io.effects.core.Either;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing the current reconciliation status
 * and history of steps.
 * It is an Aggregate Root that completely owns state progressions and produces ReconciliationEvent occurrences.
 */
public final class ReconciliationLedger<ID, K, E, C> {
    public enum Status { UNMATCHED, MATCHED, DISCREPANCY, RESOLVED }

    private final ID reconciliationId;
    private Status status = Status.UNMATCHED;
    private K itemId = null;
    private String discrepancyCode = null;
    private String resolutionType = null;
    private final List<ReconciliationStep<K, C>> history = new ArrayList<>();

    public ReconciliationLedger(ID reconciliationId) {
        this.reconciliationId = Objects.requireNonNull(reconciliationId);
    }

    public synchronized ID reconciliationId() { return reconciliationId; }
    public synchronized Status status() { return status; }
    public synchronized K itemId() { return itemId; }
    public synchronized String discrepancyCode() { return discrepancyCode; }
    public synchronized String resolutionType() { return resolutionType; }
    public synchronized List<ReconciliationStep<K, C>> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }

    public synchronized boolean isTerminal() {
        return status == Status.MATCHED || status == Status.RESOLVED;
    }

    /**
     * Records a step and transitions state internally.
     */
    private synchronized void recordStep(ReconciliationStep<K, C> step, Status nextStatus) {
        Objects.requireNonNull(step);
        Objects.requireNonNull(nextStatus);

        if (isTerminal()) {
            throw new IllegalStateException("Cannot register a step on a terminal reconciliation ledger: " + reconciliationId);
        }

        this.history.add(step);
        this.status = nextStatus;
    }

    /**
     * Behavioral Factory: Creates a new reconciliation ledger.
     */
    public static <ID, K, E, C> ReconciliationLedger<ID, K, E, C> initiate(ID reconciliationId) {
        return new ReconciliationLedger<>(reconciliationId);
    }

    /**
     * Behavioral Transition: Matches an external item with our internal record.
     */
    public synchronized Either<String, ReconciliationEvent<ID, K>> match(
        K itemId, 
        E externalItem, 
        C comment, 
        ReconciliableRequest<ID, K, E, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(externalItem);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (isTerminal()) {
            return Either.left("Cannot match on a terminal reconciliation ledger: " + status);
        }
        if (status == Status.DISCREPANCY) {
            return Either.left("Ledger is currently flagged with a discrepancy. Resolve discrepancy first.");
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateMatching(this, itemId, externalItem, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        this.itemId = itemId;

        ReconciliationStep<K, C> step = new ReconciliationStep<>(
            UUID.randomUUID().toString(),
            itemId,
            ReconciliationStep.Type.MATCH,
            comment,
            now
        );
        recordStep(step, Status.MATCHED);

        ReconciliationEvent<ID, K> event = new ItemMatched<>(reconciliationId, itemId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Flags a discrepancy for an item.
     */
    public synchronized Either<String, ReconciliationEvent<ID, K>> flagDiscrepancy(
        K itemId, 
        String discrepancyCode, 
        C comment, 
        ReconciliableRequest<ID, K, E, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(discrepancyCode);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (isTerminal()) {
            return Either.left("Cannot flag discrepancy on a terminal reconciliation ledger: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateDiscrepancy(this, itemId, discrepancyCode, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        this.itemId = itemId;
        this.discrepancyCode = discrepancyCode;

        ReconciliationStep<K, C> step = new ReconciliationStep<>(
            UUID.randomUUID().toString(),
            itemId,
            ReconciliationStep.Type.DISCREPANCY,
            comment,
            now
        );
        recordStep(step, Status.DISCREPANCY);

        ReconciliationEvent<ID, K> event = new DiscrepancyFlagged<>(reconciliationId, itemId, discrepancyCode, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Resolves a previously flagged discrepancy.
     */
    public synchronized Either<String, ReconciliationEvent<ID, K>> resolve(
        K itemId, 
        String resolutionType, 
        C comment, 
        ReconciliableRequest<ID, K, E, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(resolutionType);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.DISCREPANCY) {
            return Either.left("Can only resolve a discrepancy when status is DISCREPANCY, currently: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateResolution(this, itemId, resolutionType, comment, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        this.resolutionType = resolutionType;

        ReconciliationStep<K, C> step = new ReconciliationStep<>(
            UUID.randomUUID().toString(),
            itemId,
            ReconciliationStep.Type.RESOLVE,
            comment,
            now
        );
        recordStep(step, Status.RESOLVED);

        ReconciliationEvent<ID, K> event = new DiscrepancyResolved<>(reconciliationId, itemId, resolutionType, now);
        return Either.right(event);
    }
}
