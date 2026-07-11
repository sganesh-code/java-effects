package io.effects.samples.ecommerce.domain.models;

/**
 * Domain record representing an SLA warranty grant for a specific device.
 */
public record WarrantyGrant(String deviceId, String SLALevel) {}
