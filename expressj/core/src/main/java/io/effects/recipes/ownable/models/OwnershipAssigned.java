package io.effects.recipes.ownable.models;

import io.effects.recipes.ownable.*;
import io.effects.recipes.ownable.models.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Event published when an initial owner is successfully assigned.
 */
public record OwnershipAssigned<ID, O>(ID assetId, O ownerId, Instant occurredAt) implements OwnershipEvent<ID, O> {
    public OwnershipAssigned(ID assetId, O ownerId, Instant occurredAt) {
        this.assetId = Objects.requireNonNull(assetId);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }
}