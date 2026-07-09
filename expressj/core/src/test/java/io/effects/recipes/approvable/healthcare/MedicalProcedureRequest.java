package io.effects.recipes.approvable.healthcare;

import io.effects.Either;
import io.effects.recipes.approvable.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Healthcare domain representation of a medical procedure approval request.
 * Grounded in Alan Kay's OOP vision, it exposes no identity or initiator getters to the system,
 * relying entirely on behavior evaluation messages.
 */
public final class MedicalProcedureRequest implements ApprovableRequest<String, String, String> {
    private final String procedureName;
    private final boolean isSurgical;

    public MedicalProcedureRequest(String procedureName, boolean isSurgical) {
        this.procedureName = Objects.requireNonNull(procedureName);
        this.isSurgical = isSurgical;
    }

    public String procedureName() { return procedureName; }

    public boolean isSurgical() { return isSurgical; }

    @Override
    public InitialAssessment<String> evaluateInitialSubmission(Instant now) {
        return new InitialAssessment<>(Status.PENDING, "CLINICIAN");
    }

    @Override
    public Either<String, NextStep<String>> evaluateDecision(
        ApprovalRecord<String, String, String> record, 
        String approverId, 
        String approverRole, 
        DecisionType decisionType, 
        String comment, 
        Instant now
    ) {
        if (record.isTerminal()) {
            return Either.left("Medical procedure request has already reached a terminal state");
        }

        String required = record.requiredAuthority();
        if (required != null && !required.equalsIgnoreCase(approverRole)) {
            return Either.left("Insufficient authority: required role is " + required + " but got " + approverRole);
        }

        if (decisionType == DecisionType.REJECT) {
            return Either.right(new NextStep<>(Status.REJECTED, null));
        }

        if (decisionType == DecisionType.ESCALATE) {
            if ("CLINICIAN".equalsIgnoreCase(approverRole)) {
                return Either.right(new NextStep<>(Status.ESCALATED, "CHIEF_OF_SURGERY"));
            }
            return Either.left("Cannot escalate medical request from role: " + approverRole);
        }

        // DecisionType.APPROVE
        if ("CLINICIAN".equalsIgnoreCase(approverRole)) {
            if (isSurgical) {
                return Either.right(new NextStep<>(Status.PENDING, "CHIEF_OF_SURGERY"));
            } else {
                return Either.right(new NextStep<>(Status.APPROVED, null));
            }
        }

        if ("CHIEF_OF_SURGERY".equalsIgnoreCase(approverRole)) {
            return Either.right(new NextStep<>(Status.PENDING, "INSURANCE_REP"));
        }

        if ("INSURANCE_REP".equalsIgnoreCase(approverRole)) {
            return Either.right(new NextStep<>(Status.APPROVED, null));
        }

        return Either.left("Unsupported medical approver role: " + approverRole);
    }
}