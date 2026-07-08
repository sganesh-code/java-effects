package io.effects.recipes.adapters.approvable;

import io.effects.IO;
import io.effects.recipes.ports.approvable.ApprovalTelemetryPort;

/**
 * A telemetry adapter that logs operations to stdout for local debugging and validation.
 */
public final class LoggingApprovalTelemetryPort implements ApprovalTelemetryPort {

    @Override
    public IO<Void> recordSubmissionSuccess(String requestId) {
        return IO.delay(() -> {
            System.out.printf("[Telemetry] Request %s submitted successfully%n", requestId);
            return null;
        });
    }

    @Override
    public IO<Void> recordApprovalSuccess(String requestId) {
        return IO.delay(() -> {
            System.out.printf("[Telemetry] Request %s approved successfully%n", requestId);
            return null;
        });
    }

    @Override
    public IO<Void> recordRejection(String requestId, String reason) {
        return IO.delay(() -> {
            System.out.printf("[Telemetry] Request %s was rejected. Reason: %s%n", requestId, reason);
            return null;
        });
    }

    @Override
    public IO<Void> recordEscalation(String requestId) {
        return IO.delay(() -> {
            System.out.printf("[Telemetry] Request %s escalated%n", requestId);
            return null;
        });
    }

    @Override
    public IO<Void> recordDuration(String requestId, long durationMs) {
        return IO.delay(() -> {
            System.out.printf("[Telemetry] Request %s operation completed in %d ms%n", requestId, durationMs);
            return null;
        });
    }
}
