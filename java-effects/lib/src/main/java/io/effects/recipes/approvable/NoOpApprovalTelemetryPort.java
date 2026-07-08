package io.effects.recipes.approvable;

import io.effects.IO;

/**
 * A telemetry adapter that discards all metrics and logs.
 */
final class NoOpApprovalTelemetryPort implements ApprovalTelemetryPort {

    @Override
    public IO<Void> recordSubmissionSuccess(String requestId) {
        return IO.of(null);
    }

    @Override
    public IO<Void> recordApprovalSuccess(String requestId) {
        return IO.of(null);
    }

    @Override
    public IO<Void> recordRejection(String requestId, String reason) {
        return IO.of(null);
    }

    @Override
    public IO<Void> recordEscalation(String requestId) {
        return IO.of(null);
    }

    @Override
    public IO<Void> recordDuration(String requestId, long durationMs) {
        return IO.of(null);
    }
}
