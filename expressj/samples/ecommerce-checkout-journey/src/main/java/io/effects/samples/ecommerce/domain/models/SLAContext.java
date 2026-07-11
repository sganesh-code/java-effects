package io.effects.samples.ecommerce.domain.models;

/**
 * Domain record representing context for checking SLA entitlements.
 */
public record SLAContext(String requestType, int severityLevel) {}
