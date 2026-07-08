package io.effects.recipes.ports.reservable;

import io.effects.IO;

/**
 * Port representing system telemetry capabilities for monitoring reservation operations.
 */
public interface TelemetryPort {

    IO<Void> recordHoldDuration(String resourceId, long durationMs);

    IO<Void> recordHoldSuccess(String resourceId);

    IO<Void> recordHoldFailure(String resourceId, String reason);

    IO<Void> recordConfirmationSuccess(String resourceId);

    IO<Void> recordConfirmationFailure(String resourceId, String reason);
}
