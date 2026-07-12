package io.effects.samples.ecommerce.domain.models;

/**
 * Domain register representing measured telemetry / diagnostics for billing rating.
 */
public record DiagnosticMetric(int diagnosticCallsCount, int telemetryPayloadKb) {}
