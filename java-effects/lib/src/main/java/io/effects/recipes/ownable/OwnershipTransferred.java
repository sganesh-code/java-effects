package io.effects.recipes.ownable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when ownership of an asset is successfully transferred.
 */
public record OwnershipTransferred(String assetId, String previousOwnerId, String newOwnerId,
                                   Instant occurredAt) implements OwnershipEvent {
    public OwnershipTransferred(String assetId, String previousOwnerId, String newOwnerId, Instant occurredAt) {
        this.assetId = Objects.requireNonNull(assetId);
        this.previousOwnerId = Objects.requireNonNull(previousOwnerId);
        this.newOwnerId = Objects.requireNonNull(newOwnerId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
}
