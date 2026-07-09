package io.effects.recipes.ownable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when ownership of an asset is successfully revoked.
 */
public record OwnershipRevoked(String assetId, String previousOwnerId, Instant occurredAt) implements OwnershipEvent {
    public OwnershipRevoked(String assetId, String previousOwnerId, Instant occurredAt) {
        this.assetId = Objects.requireNonNull(assetId);
        this.previousOwnerId = Objects.requireNonNull(previousOwnerId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
}
