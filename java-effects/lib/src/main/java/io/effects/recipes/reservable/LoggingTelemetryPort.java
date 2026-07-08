package io.effects.recipes.reservable;

import io.effects.IO;

/**
 * A telemetry adapter that logs operations to stdout for local debugging and validation.
 */
final class LoggingTelemetryPort implements TelemetryPort {

    @Override
    public IO<Void> recordHoldDuration(String resourceId, long durationMs) {
        return IO.delay(() -> {
            System.out.printf("[Telemetry] Hold operation on Resource %s completed in %d ms%n", resourceId, durationMs);
            return null;
        });
    }

    @Override
    public IO<Void> recordHoldSuccess(String resourceId) {
        return IO.delay(() -> {
            System.out.printf("[Telemetry] Hold successfully created on Resource %s%n", resourceId);
            return null;
        });
    }

    @Override
    public IO<Void> recordHoldFailure(String resourceId, String reason) {
        return IO.delay(() -> {
            System.out.printf("[Telemetry] Hold failed on Resource %s. Reason: %s%n", resourceId, reason);
            return null;
        });
    }

    @Override
    public IO<Void> recordConfirmationSuccess(String resourceId) {
        return IO.delay(() -> {
            System.out.printf("[Telemetry] Hold successfully confirmed on Resource %s%n", resourceId);
            return null;
        });
    }

    @Override
    public IO<Void> recordConfirmationFailure(String resourceId, String reason) {
        return IO.delay(() -> {
            System.out.printf("[Telemetry] Confirmation failed on Resource %s. Reason: %s%n", resourceId, reason);
            return null;
        });
    }
}
