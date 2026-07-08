package io.effects.recipes.ports.approvable;

import io.effects.IO;

/**
 * Port representing system telemetry capabilities for tracking approval actions.
 */
public interface ApprovalTelemetryPort {

    IO<Void> recordSubmissionSuccess(String requestId);

    IO<Void> recordApprovalSuccess(String requestId);

    IO<Void> recordRejection(String requestId, String reason);

    IO<Void> recordEscalation(String requestId);

    IO<Void> recordDuration(String requestId, long durationMs);
}
