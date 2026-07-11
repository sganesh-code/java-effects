package io.effects.samples.ecommerce.domain.models;

/**
 * Domain record representing an entry logged for security compliance auditing.
 */
public record AuditEntry(String action, String actor, String securityHash) {}
