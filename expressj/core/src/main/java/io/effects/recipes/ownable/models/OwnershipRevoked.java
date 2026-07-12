package io.effects.recipes.ownable.models;

import io.effects.recipes.ownable.*;
import io.effects.recipes.ownable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when ownership of an asset is successfully revoked.
 */
public record OwnershipRevoked<ID, O>(ID assetId, O previousOwnerId, Instant occurredAt) implements OwnershipEvent<ID, O> {
    public OwnershipRevoked(ID assetId, O previousOwnerId, Instant occurredAt) {
        this.assetId = Objects.requireNonNull(assetId);
        this.previousOwnerId = Objects.requireNonNull(previousOwnerId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
}