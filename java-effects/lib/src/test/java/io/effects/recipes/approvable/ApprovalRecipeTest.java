package io.effects.recipes.approvable;

import io.effects.Either;
import io.effects.IO;
import io.effects.recipes.ports.approvable.*;
import io.effects.recipes.adapters.approvable.*;
import io.effects.recipes.approvable.ecommerce.ExpenseReport;
import io.effects.recipes.approvable.healthcare.MedicalProcedureRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalRecipeTest {

    // 1. E-commerce ExpenseReport Auto-Approval Path (< $100)
    @Test
    void testExpenseReportAutoApproval() {
        ApprovalProcess process = new ApprovalProcess();
        ExpenseReport report = new ExpenseReport(45.50, "Team lunch");
        
        process.register("rep-001", report).unsafeRunSync();

        Instant now = Instant.parse("2026-07-08T14:00:00Z");

        // Submit -> Auto-approved since amount < 100
        Either<String, ApprovalRecord> submitResult = process.submit("rep-001", "emp-A", now).unsafeRunSync();
        assertTrue(submitResult.isRight());
        ApprovalRecord record = submitResult.getRight();

        assertEquals(Status.APPROVED, record.status());
        assertNull(record.requiredAuthority());
        assertTrue(record.isTerminal());

        // Chronology contains exactly the initiator submission step
        List<ApprovalDecision> history = record.history();
        assertEquals(1, history.size());
        assertEquals("emp-A", history.get(0).actorId());
        assertEquals("INITIATOR", history.get(0).actorRole());
        assertEquals(DecisionType.APPROVE, history.get(0).type());
    }

    // 2. E-commerce ExpenseReport Mid-Tier Flow ($100 - $1000) & Authority Validation Invariant
    @Test
    void testExpenseReportMidTierManagerApproval() {
        InMemoryApprovalStateRepository repo = new InMemoryApprovalStateRepository();
        InMemoryApprovalEventPublisher publisher = new InMemoryApprovalEventPublisher();
        ApprovalProcess process = new ApprovalProcess(repo, publisher, new NoOpApprovalTelemetryPort());

        ExpenseReport report = new ExpenseReport(450.00, "Developer Monitor");
        process.register("rep-002", report).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T14:00:00Z");

        // Submit -> Pending manager
        Either<String, ApprovalRecord> submitResult = process.submit("rep-002", "emp-B", t0).unsafeRunSync();
        assertTrue(submitResult.isRight());
        ApprovalRecord record = submitResult.getRight();

        assertEquals(Status.PENDING, record.status());
        assertEquals("MANAGER", record.requiredAuthority());

        // Invariant: VP attempts to approve -> fails (insufficient authority for MANAGER required role)
        Either<String, ApprovalRecord> badApproval = process.approve("rep-002", "vp-X", "VP", "Looks fine to me", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(badApproval.isLeft());
        assertTrue(badApproval.getLeft().contains("Insufficient authority"));

        // Valid Manager approval -> Approved
        Either<String, ApprovalRecord> goodApproval = process.approve("rep-002", "mgr-A", "MANAGER", "Approved, within budget", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(goodApproval.isRight());
        ApprovalRecord approvedRecord = goodApproval.getRight();

        assertEquals(Status.APPROVED, approvedRecord.status());
        assertNull(approvedRecord.requiredAuthority());

        // Verify published events
        List<ApprovalEvent> events = publisher.getPublishedEvents();
        assertEquals(2, events.size());
        assertTrue(events.get(0) instanceof RequestSubmitted);
        assertTrue(events.get(1) instanceof RequestApproved);
        assertEquals("mgr-A", ((RequestApproved) events.get(1)).approverId());
    }

    // 3. Escalation Invariant & Audit History Preservation
    @Test
    void testExpenseReportEscalationAndAuditPreservation() {
        ApprovalProcess process = new ApprovalProcess();
        ExpenseReport report = new ExpenseReport(850.00, "Client Dinner");
        process.register("rep-003", report).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T15:00:00Z");

        process.submit("rep-003", "emp-C", t0).unsafeRunSync();

        // Escalated by Manager to VP
        Either<String, ApprovalRecord> escalateResult = process.escalate("rep-003", "mgr-B", "MANAGER", "VP", "Requires higher level validation", t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(escalateResult.isRight());
        ApprovalRecord escalatedRecord = escalateResult.getRight();

        assertEquals(Status.ESCALATED, escalatedRecord.status());
        assertEquals("VP", escalatedRecord.requiredAuthority());

        // Approved by VP
        Either<String, ApprovalRecord> approveResult = process.approve("rep-003", "vp-Y", "VP", "VP authorized", t0.plusSeconds(60)).unsafeRunSync();
        assertTrue(approveResult.isRight());
        ApprovalRecord finalized = approveResult.getRight();

        assertEquals(Status.APPROVED, finalized.status());

        // Verify complete chronology is fully preserved and immutable
        List<ApprovalDecision> history = finalized.history();
        assertEquals(3, history.size()); // submit, escalate, approve
        
        assertEquals("emp-C", history.get(0).actorId());
        assertEquals("INITIATOR", history.get(0).actorRole());

        assertEquals("mgr-B", history.get(1).actorId());
        assertEquals("MANAGER", history.get(1).actorRole());
        assertEquals(DecisionType.ESCALATE, history.get(1).type());

        assertEquals("vp-Y", history.get(2).actorId());
        assertEquals("VP", history.get(2).actorRole());
        assertEquals(DecisionType.APPROVE, history.get(2).type());
    }

    // 4. Healthcare Multi-step Routine Procedure Flow
    @Test
    void testHealthcareRoutineProcedureFlow() {
        ApprovalProcess process = new ApprovalProcess();
        MedicalProcedureRequest request = new MedicalProcedureRequest("Standard Blood Draw", false);
        process.register("med-001", request).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T16:00:00Z");

        // Submit -> Requires Clinician
        Either<String, ApprovalRecord> submitResult = process.submit("med-001", "pat-A", t0).unsafeRunSync();
        assertTrue(submitResult.isRight());
        assertEquals("CLINICIAN", submitResult.getRight().requiredAuthority());

        // Clinician approves -> Routine, so instantly terminal APPROVED
        Either<String, ApprovalRecord> approveResult = process.approve("med-001", "doc-A", "CLINICIAN", "Medically necessary", t0.plusSeconds(5)).unsafeRunSync();
        assertTrue(approveResult.isRight());
        ApprovalRecord finalized = approveResult.getRight();

        assertEquals(Status.APPROVED, finalized.status());
        assertTrue(finalized.isTerminal());
    }

    // 5. Healthcare Multi-step Surgical Dual/Triple Approval Flow
    @Test
    void testHealthcareSurgicalDualApprovalFlow() {
        InMemoryApprovalEventPublisher publisher = new InMemoryApprovalEventPublisher();
        ApprovalProcess process = new ApprovalProcess(new InMemoryApprovalStateRepository(), publisher, new NoOpApprovalTelemetryPort());
        
        MedicalProcedureRequest request = new MedicalProcedureRequest("Open Heart Surgery", true);
        process.register("med-002", request).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T16:00:00Z");

        // Submit -> Clinician required
        process.submit("med-002", "pat-B", t0).unsafeRunSync();

        // 1. Clinician approves -> Surgical = true, so transitions to CHIEF_OF_SURGERY required
        Either<String, ApprovalRecord> clinicianResult = process.approve("med-002", "doc-A", "CLINICIAN", "Surgical triage approved", t0.plusSeconds(5)).unsafeRunSync();
        assertTrue(clinicianResult.isRight());
        assertEquals(Status.PENDING, clinicianResult.getRight().status());
        assertEquals("CHIEF_OF_SURGERY", clinicianResult.getRight().requiredAuthority());

        // Invariant: Insurance Rep attempts to approve too early -> rejected
        Either<String, ApprovalRecord> prematureInsuranceResult = process.approve("med-002", "ins-rep-A", "INSURANCE_REP", "Pre-authorized", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(prematureInsuranceResult.isLeft());

        // 2. Chief of Surgery approves -> Transitions to INSURANCE_REP required
        Either<String, ApprovalRecord> chiefResult = process.approve("med-002", "chief-doc-X", "CHIEF_OF_SURGERY", "OR and surgeon allocated", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(chiefResult.isRight());
        assertEquals(Status.PENDING, chiefResult.getRight().status());
        assertEquals("INSURANCE_REP", chiefResult.getRight().requiredAuthority());

        // 3. Insurance Representative approves -> Terminal APPROVED
        Either<String, ApprovalRecord> insuranceResult = process.approve("med-002", "ins-rep-A", "INSURANCE_REP", "Full coverage authorized", t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(insuranceResult.isRight());
        ApprovalRecord finalized = insuranceResult.getRight();

        assertEquals(Status.APPROVED, finalized.status());
        assertTrue(finalized.isTerminal());
        assertEquals(4, finalized.history().size()); // submit, clinician, chief, insurance
    }

    // 6. Invariant Violation: No action after terminal state, No Approval after Rejection, and Idempotency
    @Test
    void testWorkflowInvariantsAndIdempotency() {
        ApprovalProcess process = new ApprovalProcess();
        ExpenseReport report = new ExpenseReport(150.00, "Office chair");
        process.register("rep-004", report).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T17:00:00Z");

        process.submit("rep-004", "emp-D", t0).unsafeRunSync();

        // Reject request -> Terminal state REJECTED
        Either<String, ApprovalRecord> rejectResult = process.reject("rep-004", "mgr-C", "MANAGER", "Chair too expensive", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(rejectResult.isRight());
        ApprovalRecord record = rejectResult.getRight();
        assertEquals(Status.REJECTED, record.status());

        // Invariant: Trying to approve a rejected request is prohibited
        Either<String, ApprovalRecord> tryApprove = process.approve("rep-004", "mgr-C", "MANAGER", "Let me change my mind", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(tryApprove.isLeft());
        assertTrue(tryApprove.getLeft().contains("Cannot approve a rejected request"));

        // Idempotency: Re-rejecting returns identical terminal record success
        Either<String, ApprovalRecord> reReject = process.reject("rep-004", "mgr-C", "MANAGER", "Chair too expensive", t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(reReject.isRight());
        assertEquals(record.history().size(), reReject.getRight().history().size());
    }

    // 7. Verification of custom Telemetry and DI Ports Injection
    @Test
    void testTelemetryPortsInjectionSpy() {
        class TelemetrySpy implements ApprovalTelemetryPort {
            int submissions = 0;
            int approvals = 0;
            int rejections = 0;

            @Override
            public IO<Void> recordSubmissionSuccess(String requestId) {
                return IO.delay(() -> { submissions++; return null; });
            }

            @Override
            public IO<Void> recordApprovalSuccess(String requestId) {
                return IO.delay(() -> { approvals++; return null; });
            }

            @Override
            public IO<Void> recordRejection(String requestId, String reason) {
                return IO.delay(() -> { rejections++; return null; });
            }

            @Override
            public IO<Void> recordEscalation(String requestId) {
                return IO.of(null);
            }

            @Override
            public IO<Void> recordDuration(String requestId, long durationMs) {
                return IO.of(null);
            }
        }

        TelemetrySpy spy = new TelemetrySpy();
        ApprovalProcess process = new ApprovalProcess(
            new InMemoryApprovalStateRepository(),
            new InMemoryApprovalEventPublisher(),
            spy
        );

        ExpenseReport report = new ExpenseReport(10.00, "Notebook");
        process.register("rep-005", report).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T18:00:00Z");

        // Submit -> Auto-approved since < 100
        process.submit("rep-005", "emp-E", t0).unsafeRunSync();

        assertEquals(1, spy.submissions);
    }
}
