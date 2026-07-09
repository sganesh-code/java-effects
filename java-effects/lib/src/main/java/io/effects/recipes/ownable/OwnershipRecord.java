package io.effects.recipes.ownable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A non-anemic, thread-safe domain state ledger representing the current owner 
 * and immutable history of ownership adjustments.
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
     * Records an ownership change and transitions the state of the record.
     */
    public synchronized void recordTransfer(OwnershipStep step, String nextOwnerId) {
        Objects.requireNonNull(step);
        this.history.add(step);
        this.currentOwnerId = nextOwnerId; // Null signifies revocation
    }
}
