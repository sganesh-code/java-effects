package io.effects.samples.ecommerce.domain;

import io.effects.core.Either;
import io.effects.recipes.auditable.*;
import io.effects.recipes.auditable.models.*;
import io.effects.samples.ecommerce.domain.models.AuditCumulativeState;
import io.effects.samples.ecommerce.domain.models.AuditEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ComplianceAuditor implements AuditableRequest<String, AuditEntry, AuditCumulativeState> {
    private final String orderId;
    private final AuditableProcess<String, AuditEntry, AuditCumulativeState> auditProcess;

    public ComplianceAuditor(String orderId) {
        this.orderId = orderId;
        this.auditProcess = new AuditableProcess<>();
    }

    public void initiate() {
        auditProcess.register(orderId, this).unsafeRunSync();
        auditProcess.initiate(orderId).unsafeRunSync();
    }

    public void record(String actorId, AuditEntry entry, Instant time) {
        var res = auditProcess.register(orderId, actorId, entry, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Audit recording failed: " + res.getLeft());
        }
    }

    public AuditCumulativeState replay() {
        var res = auditProcess.replay(orderId).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Audit replay failed: " + res.getLeft());
        }
        AuditCumulativeState state = res.getRight();
        DomainLogger.info("[AUDIT] Replayed security state compliance status: " + (state.isCompliant() ? "SECURE & COMPLIANT" : "NON-COMPLIANT"));
        DomainLogger.info("[AUDIT] Recorded Chronology:");
        state.recordedActions().forEach(act -> DomainLogger.info("  -> " + act));
        return state;
    }

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
