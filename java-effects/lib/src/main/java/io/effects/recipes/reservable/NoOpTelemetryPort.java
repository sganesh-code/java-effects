package io.effects.recipes.reservable;

import io.effects.IO;

/**
 * A telemetry adapter that discards all logs and metrics.
 */
public final class NoOpTelemetryPort implements TelemetryPort {

    @Override
    public IO<Void> recordHoldDuration(String resourceId, long durationMs) {
        return IO.of(null);
    }

    @Override
    public IO<Void> recordHoldSuccess(String resourceId) {
        return IO.of(null);
    }

    @Override
    public IO<Void> recordHoldFailure(String resourceId, String reason) {
        return IO.of(null);
    }

    @Override
    public IO<Void> recordConfirmationSuccess(String resourceId) {
        return IO.of(null);
    }

    @Override
    public IO<Void> recordConfirmationFailure(String resourceId, String reason) {
        return IO.of(null);
    }
}
