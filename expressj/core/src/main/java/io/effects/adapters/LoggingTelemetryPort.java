package io.effects.adapters;

import io.effects.IO;
import io.effects.ports.TelemetryPort;

/**
 * A generalized telemetry adapter that logs metrics and latencies to standard output.
 */
public final class LoggingTelemetryPort implements TelemetryPort {

    @Override
    public IO<Void> recordSuccess(String context, String operationId) {
        return IO.delay(() -> {
            System.out.printf("[Telemetry] [%s] %s: Success%n", context, operationId);
            return null;
        });
    }

    @Override
    public IO<Void> recordFailure(String context, String operationId, String reason) {
        return IO.delay(() -> {
            System.out.printf("[Telemetry] [%s] %s: Failure. Reason: %s%n", context, operationId, reason);
            return null;
        });
    }

    @Override
    public IO<Void> recordDuration(String context, String operationId, long durationMs) {
        return IO.delay(() -> {
            System.out.printf("[Telemetry] [%s] %s completed in %d ms%n", context, operationId, durationMs);
            return null;
        });
    }
}
