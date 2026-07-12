package io.effects.samples.ecommerce.domain.models;

/**
 * Domain register representing context for checking SLA entitlements.
 */
public record SLAContext(String requestType, int severityLevel) {}
