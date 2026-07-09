package io.effects.recipes.ownable;

import io.effects.Either;
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
public final class OwnershipRecord {
    private final String assetId;
    private String currentOwnerId;
    private final List<OwnershipStep> history = new ArrayList<>();

    public OwnershipRecord(String assetId) {
        this.assetId = Objects.requireNonNull(assetId);
    }

    public synchronized String assetId() { return assetId; }
    public synchronized String currentOwnerId() { return currentOwnerId; }
    public synchronized List<OwnershipStep> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }

    public synchronized boolean hasOwner() {
        return currentOwnerId != null;
    }

    /**
     * Records an ownership change and transitions the state of the record internally.
     */
    private synchronized void recordTransfer(OwnershipStep step, String nextOwnerId) {
        Objects.requireNonNull(step);
        this.history.add(step);
        this.currentOwnerId = nextOwnerId; // Null signifies revocation
    }

    /**
     * Behavioral Factory: Evaluates, assigns, and creates the OwnershipRecord.
     */
    public static Either<String, TransitionResult<OwnershipRecord, OwnershipEvent>> assign(
        String assetId, 
        String ownerId, 
        OwnableRequest asset, 
        Instant now
    ) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(asset);
        Objects.requireNonNull(now);

        OwnershipRecord record = new OwnershipRecord(assetId);

        // Domain validation
        Either<String, Void> eitherValid = asset.evaluateInitialAssignment(ownerId, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        OwnershipStep step = new OwnershipStep(
            UUID.randomUUID().toString(),
            ownerId,
            OwnershipStep.Type.ASSIGN,
            "Initial ownership assignment",
            now
        );
        record.recordTransfer(step, ownerId);

        OwnershipEvent event = new OwnershipAssigned(assetId, ownerId, now);
        return Either.right(new TransitionResult<>(record, event));
    }

    /**
     * Behavioral Transition: Transfers ownership of an asset.
     */
    public synchronized Either<String, OwnershipEvent> transfer(
        String currentOwnerId, 
        String proposedOwnerId, 
        String actorId, 
        String comment, 
        OwnableRequest asset, 
        Instant now
    ) {
        Objects.requireNonNull(currentOwnerId);
        Objects.requireNonNull(proposedOwnerId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(asset);
        Objects.requireNonNull(now);

        if (statusMatches(proposedOwnerId)) {
            return Either.right(null); // Idempotent success
        }
        if (!hasOwner()) {
            return Either.left("Cannot transfer: asset has no active owner");
        }
        if (!this.currentOwnerId.equals(currentOwnerId)) {
            return Either.left("Current owner mismatch: expected " + this.currentOwnerId + " but got " + currentOwnerId);
        }

        // Domain validation
        Either<String, Void> eitherValid = asset.evaluateTransfer(this, currentOwnerId, proposedOwnerId, actorId, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        OwnershipStep step = new OwnershipStep(
            UUID.randomUUID().toString(),
            actorId,
            OwnershipStep.Type.TRANSFER,
            comment,
            now
        );
        recordTransfer(step, proposedOwnerId);

        OwnershipEvent event = new OwnershipTransferred(assetId, currentOwnerId, proposedOwnerId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Revokes ownership of an asset.
     */
    public synchronized Either<String, OwnershipEvent> revoke(
        String currentOwnerId, 
        String actorId, 
        String reason, 
        OwnableRequest asset, 
        Instant now
    ) {
        Objects.requireNonNull(currentOwnerId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(asset);
        Objects.requireNonNull(now);

        if (!hasOwner()) {
            return Either.right(null); // Idempotent success
        }
        if (!this.currentOwnerId.equals(currentOwnerId)) {
            return Either.left("Current owner mismatch: expected " + this.currentOwnerId + " but got " + currentOwnerId);
        }

        // Domain validation
        Either<String, Void> eitherValid = asset.evaluateTransfer(this, currentOwnerId, null, actorId, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        OwnershipStep step = new OwnershipStep(
            UUID.randomUUID().toString(),
            actorId,
            OwnershipStep.Type.REVOKE,
            reason,
            now
        );
        recordTransfer(step, null);

        OwnershipEvent event = new OwnershipRevoked(assetId, currentOwnerId, now);
        return Either.right(event);
    }

    private synchronized boolean statusMatches(String proposedOwnerId) {
        return currentOwnerId != null && currentOwnerId.equals(proposedOwnerId);
    }
}
