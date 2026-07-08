package io.effects.ports;

import io.effects.IO;

/**
 * A generalized, type-safe telemetry port for recording operational success, failures, and execution latencies.
 */
public interface TelemetryPort {

    /**
     * Records a successful operation execution.
     */
    IO<Void> recordSuccess(String context, String operationId);

    /**
     * Records a failed operation execution.
     */
    IO<Void> recordFailure(String context, String operationId, String reason);

    /**
     * Records an execution duration/latency.
     */
    IO<Void> recordDuration(String context, String operationId, long durationMs);
}
