package io.effects.recipes.reservable;

import io.effects.IO;

/**
 * A Port (Interface) representing metrics and telemetry recording capability.
 * All operations return lazy monadic IO blocks to protect effect-boundaries.
 */
public interface TelemetryPort {

    /**
     * Records the duration taken to process a hold request.
     */
    IO<Void> recordHoldDuration(String resourceId, long durationMs);

    /**
     * Increments the count of successful hold operations on a resource.
     */
    IO<Void> recordHoldSuccess(String resourceId);

    /**
     * Increments the count of failed hold operations on a resource with a rejection reason.
     */
    IO<Void> recordHoldFailure(String resourceId, String reason);

    /**
     * Increments the count of successful hold confirmation operations.
     */
    IO<Void> recordConfirmationSuccess(String resourceId);

    /**
     * Increments the count of failed hold confirmation operations with a rejection reason.
     */
    IO<Void> recordConfirmationFailure(String resourceId, String reason);
}
