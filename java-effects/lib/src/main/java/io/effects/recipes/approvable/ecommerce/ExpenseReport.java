package io.effects.recipes.approvable.ecommerce;

import io.effects.Either;
import io.effects.recipes.approvable.*;
import java.time.Instant;
import java.util.Objects;

/**
 * E-commerce / FinTech domain representation of an expense report requiring approval.
 * It is completely stateless and synchronous! It contains NO monadic references (no IO)
 * or concurrency boilerplate, delegating state-process evaluation to the record provided.
 */
public final class ExpenseReport implements ApprovableRequest {
    private final String reportId;
    private final String employeeId;
    private final double amount;
    private final String purpose;

    public ExpenseReport(String reportId, String employeeId, double amount, String purpose) {
        this.reportId = Objects.requireNonNull(reportId);
        this.employeeId = Objects.requireNonNull(employeeId);
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        this.amount = amount;
        this.purpose = Objects.requireNonNull(purpose);
    }

    @Override
    public String requestId() { return reportId; }

    @Override
    public String initiatorId() { return employeeId; }

    @Override
    public InitialAssessment evaluateInitialSubmission(Instant now) {
        if (amount < 100.0) {
            return new InitialAssessment(Status.APPROVED, null);
        } else if (amount < 1000.0) {
            return new InitialAssessment(Status.PENDING, "MANAGER");
        } else {
            return new InitialAssessment(Status.PENDING, "VP");
        }
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
            return Either.left("Request has already reached a terminal state");
        }

        String required = approvalRecord.requiredAuthority();
        if (required != null && !required.equalsIgnoreCase(approverRole)) {
            return Either.left("Insufficient authority: required role is " + required + " but got " + approverRole);
        }

        if (decisionType == DecisionType.REJECT) {
            return Either.right(new NextStep(Status.REJECTED, null));
        }

        if (decisionType == DecisionType.ESCALATE) {
            if ("MANAGER".equalsIgnoreCase(approverRole)) {
                return Either.right(new NextStep(Status.ESCALATED, "VP"));
            }
            return Either.left("Cannot escalate request from role: " + approverRole);
        }

        // DecisionType.APPROVE
        if ("MANAGER".equalsIgnoreCase(approverRole)) {
            if (amount >= 1000.0) {
                // Should not happen if initial evaluation routed to VP, but protects against manual state overrides
                return Either.right(new NextStep(Status.PENDING, "VP"));
            }
            return Either.right(new NextStep(Status.APPROVED, null));
        }

        if ("VP".equalsIgnoreCase(approverRole)) {
            return Either.right(new NextStep(Status.APPROVED, null));
        }

        return Either.left("Unsupported approver role: " + approverRole);
    }
}
