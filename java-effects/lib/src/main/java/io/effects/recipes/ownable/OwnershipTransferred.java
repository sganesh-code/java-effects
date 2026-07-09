package io.effects.recipes.ownable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when ownership of an asset is successfully transferred.
 */
public record OwnershipTransferred<ID, O>(ID assetId, O previousOwnerId, O newOwnerId, Instant occurredAt) implements OwnershipEvent<ID, O> {
    public OwnershipTransferred(ID assetId, O previousOwnerId, O newOwnerId, Instant occurredAt) {
        this.assetId = Objects.requireNonNull(assetId);
        this.previousOwnerId = Objects.requireNonNull(previousOwnerId);
        this.newOwnerId = Objects.requireNonNull(newOwnerId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
}