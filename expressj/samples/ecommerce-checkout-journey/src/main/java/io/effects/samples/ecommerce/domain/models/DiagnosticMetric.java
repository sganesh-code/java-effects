package io.effects.samples.ecommerce.domain.models;

/**
 * Domain record representing measured telemetry / diagnostics for billing rating.
 */
public record DiagnosticMetric(int diagnosticCallsCount, int telemetryPayloadKb) {}
