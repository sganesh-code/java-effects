package io.effects.recipes.meterable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the meterable recipe.
 */
public interface MeterableEvent<ID> {

    /**
     * Unique identifier of the metered account.
     */
    ID accountId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();

    /**
     * Event published when a meter billing cycle is started.
     */
    record MeterStarted<ID>(ID accountId, Instant occurredAt) implements MeterableEvent<ID> {}

    /**
     * Event published when a usage consumption tick is recorded.
     */
    record UsageRecorded<ID, U>(ID accountId, UsageStep<U> step, Instant occurredAt) implements MeterableEvent<ID> {}

    /**
     * Event published when a billing cycle is rated and finalized.
     */
    record MeterRated<ID, R>(ID accountId, R rating, Instant occurredAt) implements MeterableEvent<ID> {}
}