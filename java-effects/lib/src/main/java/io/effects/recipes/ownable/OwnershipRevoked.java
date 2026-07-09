package io.effects.recipes.ownable;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when ownership of an asset is successfully revoked.
 */
public final class OwnershipRevoked implements OwnershipEvent {
    private final String assetId;
    private final String previousOwnerId;
    private final Instant occurredAt;

    public OwnershipRevoked(String assetId, String previousOwnerId, Instant occurredAt) {
        this.assetId = Objects.requireNonNull(assetId);
        this.previousOwnerId = Objects.requireNonNull(previousOwnerId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @Override
    public String assetId() { return assetId; }

    public String previousOwnerId() { return previousOwnerId; }

    @Override
    public Instant occurredAt() { return occurredAt; }
}
