package io.effects.adapters;

import io.effects.IO;
import io.effects.ports.TelemetryPort;

/**
 * A generalized telemetry adapter that silently discards all metric and latency events.
 */
public final class NoOpTelemetryPort implements TelemetryPort {

    @Override
    public IO<Void> recordSuccess(String context, String operationId) {
        return IO.of(null);
    }

    @Override
    public IO<Void> recordFailure(String context, String operationId, String reason) {
        return IO.of(null);
    }

    @Override
    public IO<Void> recordDuration(String context, String operationId, long durationMs) {
        return IO.of(null);
    }
}
