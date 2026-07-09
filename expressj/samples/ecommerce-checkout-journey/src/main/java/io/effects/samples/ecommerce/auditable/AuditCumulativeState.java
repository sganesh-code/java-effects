package io.effects.samples.ecommerce.auditable;

import java.util.List;

public record AuditCumulativeState(List<String> recordedActions, boolean isCompliant) {}
