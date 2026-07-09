package io.effects.recipes.ownable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an initial owner is successfully assigned.
 */
public record OwnershipAssigned(String assetId, String ownerId, Instant occurredAt) implements OwnershipEvent {
    public OwnershipAssigned(String assetId, String ownerId, Instant occurredAt) {
        this.assetId = Objects.requireNonNull(assetId);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
}
