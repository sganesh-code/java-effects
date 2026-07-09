package io.effects.recipes.ownable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an initial owner is successfully assigned.
 */
public final class OwnershipAssigned implements OwnershipEvent {
    private final String assetId;
    private final String ownerId;
    private final Instant occurredAt;

    public OwnershipAssigned(String assetId, String ownerId, Instant occurredAt) {
        this.assetId = Objects.requireNonNull(assetId);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String assetId() { return assetId; }

    public String ownerId() { return ownerId; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
