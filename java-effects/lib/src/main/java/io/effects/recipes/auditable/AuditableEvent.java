package io.effects.recipes.auditable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the auditable recipe.
 */
public interface AuditableEvent<ID> {

    /**
     * Unique identifier of the audited asset.
     */
    ID assetId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();

    /**
     * Event published when a new audit step is successfully recorded.
     */
    record AuditRecorded<ID, E>(ID assetId, AuditStep<E> step, Instant occurredAt) implements AuditableEvent<ID> {}

    /**
     * Event published when a compact state snapshot is successfully taken.
     */
    record SnapshotTaken<ID, S>(ID assetId, S state, Instant occurredAt) implements AuditableEvent<ID> {}
}