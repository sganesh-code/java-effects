package io.effects.samples.ecommerce.auditable;

import io.effects.Either;
import io.effects.recipes.auditable.AuditableRequest;
import io.effects.recipes.auditable.AuditLedger;
import io.effects.recipes.auditable.AuditStep;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SecurityComplianceAuditLogger implements AuditableRequest<String, AuditEntry, AuditCumulativeState> {

    @Override
    public Either<String, Void> evaluateEntry(AuditLedger<String, AuditEntry> ledger, AuditEntry detail, Instant now) {
        if (detail.action() == null || detail.action().isBlank()) {
            return Either.left("Audit action detail cannot be empty.");
        }
        if (detail.actor() == null || detail.actor().isBlank()) {
            return Either.left("Audit actor identity must be specified.");
        }
        return Either.right(null);
    }

    @Override
    public AuditCumulativeState reconstructState(List<AuditStep<AuditEntry>> history) {
        List<String> actions = new ArrayList<>();
        boolean isCompliant = true;
        for (AuditStep<AuditEntry> step : history) {
            AuditEntry entry = step.detail();
            actions.add(entry.actor() + " executed " + entry.action());
            if (entry.action().contains("VIOLATION") || entry.action().contains("BREACH")) {
                isCompliant = false;
            }
        }
        return new AuditCumulativeState(actions, isCompliant);
    }

    @Override
    public String explainDecision(List<AuditStep<AuditEntry>> history, String decisionStepId) {
        for (AuditStep<AuditEntry> step : history) {
            if (step.stepId().equals(decisionStepId)) {
                AuditEntry entry = step.detail();
                return "At " + step.timestamp() + ", actor [" + entry.actor() + "] performed operation [" + entry.action() + "] under cryptolink hash: " + step.hash();
            }
        }
        return "No audit step found matching ID: " + decisionStepId;
    }
}
