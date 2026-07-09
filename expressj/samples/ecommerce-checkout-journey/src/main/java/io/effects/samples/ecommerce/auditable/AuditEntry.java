package io.effects.samples.ecommerce.auditable;

public record AuditEntry(String action, String actor, String securityHash) {}
