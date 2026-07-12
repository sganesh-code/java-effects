package io.effects.recipes.ownable;

import io.effects.recipes.ownable.models.*;

import io.effects.core.Either;
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing the current owner 
 * and immutable history of ownership adjustments.
 * It is an Aggregate Root that completely owns ownership state transitions and produces OwnershipEvents.
 */
public final class OwnershipRecord<ID, O> {
    private final ID assetId;
    private O currentOwner;
    private final List<OwnershipStep<O>> history = new ArrayList<>();

    public OwnershipRecord(ID assetId) {
        this.assetId = Objects.requireNonNull(assetId);
    }

    public synchronized ID assetId() { return assetId; }
    public synchronized O currentOwner() { return currentOwner; }
    public synchronized List<OwnershipStep<O>> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }

    public synchronized boolean hasOwner() {
        return currentOwner != null;
    }

    /**
     * Records an ownership change and transitions the state of the register internally.
     */
    private synchronized void recordTransfer(OwnershipStep<O> step, O nextOwner) {
        Objects.requireNonNull(step);
        this.history.add(step);
        this.currentOwner = nextOwner; // Null signifies revocation
    }

    /**
     * Behavioral Factory: Evaluates, assigns, and creates the OwnershipRecord.
     */
    public static <ID, O> Either<String, TransitionResult<OwnershipRecord<ID, O>, OwnershipEvent<ID, O>>> assign(
        ID assetId, 
        O owner, 
        OwnableRequest<ID, O> asset, 
        Instant now
    ) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(asset);
        Objects.requireNonNull(now);

        OwnershipRecord<ID, O> record = new OwnershipRecord<>(assetId);

        // Domain validation
        Either<String, Void> eitherValid = asset.evaluateInitialAssignment(owner, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        OwnershipStep<O> step = new OwnershipStep<>(
            UUID.randomUUID().toString(),
            owner,
            OwnershipStep.Type.ASSIGN,
            "Initial ownership assignment",
            now
        );
        record.recordTransfer(step, owner);

        OwnershipEvent<ID, O> event = new OwnershipAssigned<>(assetId, owner, now);
        return Either.right(new TransitionResult<>(record, event));
    }

    /**
     * Behavioral Transition: Transfers ownership of an asset.
     */
    public synchronized Either<String, OwnershipEvent<ID, O>> transfer(
        O currentOwner, 
        O proposedOwner, 
        O actor, 
        String comment, 
        OwnableRequest<ID, O> asset, 
        Instant now
    ) {
        Objects.requireNonNull(currentOwner);
        Objects.requireNonNull(proposedOwner);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(asset);
        Objects.requireNonNull(now);

        if (statusMatches(proposedOwner)) {
            return Either.right(null); // Idempotent success
        }
        if (!hasOwner()) {
            return Either.left("Cannot transfer: asset has no active owner");
        }
        if (!this.currentOwner.equals(currentOwner)) {
            return Either.left("Current owner mismatch: expected " + this.currentOwner + " but got " + currentOwner);
        }

        // Domain validation
        Either<String, Void> eitherValid = asset.evaluateTransfer(this, currentOwner, proposedOwner, actor, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        OwnershipStep<O> step = new OwnershipStep<>(
            UUID.randomUUID().toString(),
            actor,
            OwnershipStep.Type.TRANSFER,
            comment,
            now
        );
        recordTransfer(step, proposedOwner);

        OwnershipEvent<ID, O> event = new OwnershipTransferred<>(assetId, currentOwner, proposedOwner, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Revokes ownership of an asset.
     */
    public synchronized Either<String, OwnershipEvent<ID, O>> revoke(
        O currentOwner, 
        O actor, 
        String reason, 
        OwnableRequest<ID, O> asset, 
        Instant now
    ) {
        Objects.requireNonNull(currentOwner);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(asset);
        Objects.requireNonNull(now);

        if (!hasOwner()) {
            return Either.right(null); // Idempotent success
        }
        if (!this.currentOwner.equals(currentOwner)) {
            return Either.left("Current owner mismatch: expected " + this.currentOwner + " but got " + currentOwner);
        }

        // Domain validation
        Either<String, Void> eitherValid = asset.evaluateTransfer(this, currentOwner, null, actor, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        OwnershipStep<O> step = new OwnershipStep<>(
            UUID.randomUUID().toString(),
            actor,
            OwnershipStep.Type.REVOKE,
            reason,
            now
        );
        recordTransfer(step, null);

        OwnershipEvent<ID, O> event = new OwnershipRevoked<>(assetId, currentOwner, now);
        return Either.right(event);
    }

    private synchronized boolean statusMatches(O proposedOwner) {
        return currentOwner != null && currentOwner.equals(proposedOwner);
    }
}