package io.effects.recipes.approvable.healthcare;

import io.effects.Either;
import io.effects.recipes.approvable.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Healthcare domain representation of a medical procedure approval request.
 * Completely stateless, synchronous, and pure, delegating audit-trail state analysis
 * to the record provided.
 */
public final class MedicalProcedureRequest implements ApprovableRequest {
    private final String procedureId;
    private final String patientId;
    private final String procedureName;
    private final boolean isSurgical;

    public MedicalProcedureRequest(String procedureId, String patientId, String procedureName, boolean isSurgical) {
        this.procedureId = Objects.requireNonNull(procedureId);
        this.patientId = Objects.requireNonNull(patientId);
        this.procedureName = Objects.requireNonNull(procedureName);
        this.isSurgical = isSurgical;
    }

    @Override
    public String requestId() { return procedureId; }

    @Override
    public String initiatorId() { return patientId; }

    @Override
    public InitialAssessment evaluateInitialSubmission(Instant now) {
        // All medical requests start by requiring a Clinician's diagnostic verification.
        return new InitialAssessment(Status.PENDING, "CLINICIAN");
    }

    @Override
    public Either<String, NextStep> evaluateDecision(
        ApprovalRecord approvalRecord,
        String approverId, 
        String approverRole, 
        DecisionType decisionType, 
        String comment, 
        Instant now
    ) {
        if (approvalRecord.isTerminal()) {
            return Either.left("Medical procedure request has already reached a terminal state");
        }

        String required = approvalRecord.requiredAuthority();
        if (required != null && !required.equalsIgnoreCase(approverRole)) {
            return Either.left("Insufficient authority: required role is " + required + " but got " + approverRole);
        }

        if (decisionType == DecisionType.REJECT) {
            return Either.right(new NextStep(Status.REJECTED, null));
        }

        if (decisionType == DecisionType.ESCALATE) {
            if ("CLINICIAN".equalsIgnoreCase(approverRole)) {
                return Either.right(new NextStep(Status.ESCALATED, "CHIEF_OF_SURGERY"));
            }
            return Either.left("Cannot escalate medical request from role: " + approverRole);
        }

        // DecisionType.APPROVE
        if ("CLINICIAN".equalsIgnoreCase(approverRole)) {
            if (isSurgical) {
                // Dual approval step 1: clinician verifies, escalates to chief of surgery
                return Either.right(new NextStep(Status.PENDING, "CHIEF_OF_SURGERY"));
            } else {
                // Routine/non-surgical procedure: single approval is sufficient
                return Either.right(new NextStep(Status.APPROVED, null));
            }
        }

        if ("CHIEF_OF_SURGERY".equalsIgnoreCase(approverRole)) {
            // Dual approval step 2: Chief of Surgery approves, needs insurance rep to finalize
            return Either.right(new NextStep(Status.PENDING, "INSURANCE_REP"));
        }

        if ("INSURANCE_REP".equalsIgnoreCase(approverRole)) {
            // Dual approval step 3: Insurance representative authorizes
            return Either.right(new NextStep(Status.APPROVED, null));
        }

        return Either.left("Unsupported medical approver role: " + approverRole);
    }
}
