package io.effects.recipes.auditable;

import io.effects.Either;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing an immutable, cryptographically chained audit trail.
 * It is an Aggregate Root that encapsulates all invariants and coordinates state validation via double-dispatch.
 */
public final class AuditLedger<ID, E> {
    private final ID assetId;
    private final List<AuditStep<E>> history = new ArrayList<>();

    public AuditLedger(ID assetId) {
        this.assetId = Objects.requireNonNull(assetId);
    }

    public synchronized ID assetId() {
        return assetId;
    }

    public synchronized List<AuditStep<E>> history() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    private synchronized void recordStep(AuditStep<E> step) {
        history.add(step);
    }

    /**
     * Clear the local in-memory history of audit steps (e.g. after a compact state snapshot has been taken).
     */
    public synchronized void compact() {
        this.history.clear();
    }

    /**
     * Behavioral Transition: Records a new audit entry, cryptographically chaining it to the previous hash.
     */
    public synchronized Either<String, AuditStep<E>> recordEntry(
        String actorId, 
        E detail, 
        AuditableRequest<ID, E, ?> request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateEntry(this, detail, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        String previousHash = history.isEmpty() ? "" : history.get(history.size() - 1).hash();
        String stepId = UUID.randomUUID().toString();
        String hash = AuditStep.computeHash(stepId, actorId, detail, previousHash, now);

        AuditStep<E> step = new AuditStep<>(stepId, actorId, detail, hash, now);
        recordStep(step);
        return Either.right(step);
    }
}