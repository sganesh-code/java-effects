package io.effects.samples.ecommerce.domain;

import io.effects.samples.ecommerce.domain.models.DiagnosticMetric;
import io.effects.samples.ecommerce.domain.models.AuditEntry;


import java.time.Instant;
import java.util.List;

/**
 * Collaboration object that coordinates post-sale customer support systems:
 * SLA usage tracking, automated billing schedules, and compliance auditing.
 */
public class PostSaleSupport {
    private final SLAUsageTracker usageTracker;
    private final BillingScheduler billingScheduler;
    private final ComplianceAuditor complianceAuditor;
    private boolean supportUsageInitiated = false;

    public PostSaleSupport(String customerEmail, String orderId) {
        this.usageTracker = new SLAUsageTracker(customerEmail);
        this.billingScheduler = new BillingScheduler(orderId);
        this.complianceAuditor = new ComplianceAuditor(orderId);
    }

    public void startSupportSession(Instant time) {
        if (!supportUsageInitiated) {
            usageTracker.initiate();
            supportUsageInitiated = true;
        }
        usageTracker.start(time);
    }

    public void logDiagnosticTelemetry(DiagnosticMetric metric, Instant time) {
        usageTracker.recordUsage(metric, time);
    }

    public void settleSupportBilling(String schedulerActor, Instant runTime, Instant scheduleTime) {
        // Group the low-level scheduling and rating operations into a unified billing settlement workflow
        billingScheduler.scheduleTask(schedulerActor, runTime, scheduleTime);
        billingScheduler.fire(schedulerActor, runTime);
        usageTracker.rate(runTime);
    }

    public void verifySecurityCompliance(String auditorActor, List<AuditEntry> auditTrail, Instant runTime) {
        // Encapsulate chronological audit logging and playback into a single compliance verification message
        DomainLogger.info("[AUDIT] Appending administrative order events into tamper-evident ledger...");
        complianceAuditor.initiate();

        for (int i = 0; i < auditTrail.size(); i++) {
            complianceAuditor.record(auditorActor, auditTrail.get(i), runTime.plusSeconds(i * 10L));
        }

        DomainLogger.info("[AUDIT] Administrative audit trailing completed. Replaying chronological ledger state...");
        complianceAuditor.replay();
    }
}
