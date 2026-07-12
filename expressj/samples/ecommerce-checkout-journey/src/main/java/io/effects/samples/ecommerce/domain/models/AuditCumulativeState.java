package io.effects.samples.ecommerce.domain.models;

import java.util.List;

/**
 * Domain register representing replayed compliance audit ledger state.
 */
public record AuditCumulativeState(List<String> recordedActions, boolean isCompliant) {}
