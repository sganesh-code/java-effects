package io.effects.recipes.ownable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when ownership of an asset is successfully transferred.
 */
public final class OwnershipTransferred implements OwnershipEvent {
    private final String assetId;
    private final String previousOwnerId;
    private final String newOwnerId;
    private final Instant occurredAt;

    public OwnershipTransferred(String assetId, String previousOwnerId, String newOwnerId, Instant occurredAt) {
        this.assetId = Objects.requireNonNull(assetId);
        this.previousOwnerId = Objects.requireNonNull(previousOwnerId);
        this.newOwnerId = Objects.requireNonNull(newOwnerId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String assetId() { return assetId; }

    public String previousOwnerId() { return previousOwnerId; }

    public String newOwnerId() { return newOwnerId; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
