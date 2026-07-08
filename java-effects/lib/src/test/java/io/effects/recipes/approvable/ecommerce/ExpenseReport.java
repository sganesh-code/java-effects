package io.effects.recipes.approvable.ecommerce;

import io.effects.Either;
import io.effects.recipes.approvable.*;
import java.time.Instant;
import java.util.Objects;

/**
 * E-commerce / FinTech domain representation of an expense report requiring approval.
 * Grounded in Alan Kay's OOP vision, it exposes no identity or initiator getters to the system,
 * relying entirely on behavior evaluation messages.
 */
public final class ExpenseReport implements ApprovableRequest {
    private final double amount;
    private final String purpose;

    public ExpenseReport(double amount, String purpose) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        this.amount = amount;
        this.purpose = Objects.requireNonNull(purpose);
    }

    public double amount() { return amount; }

    public String purpose() { return purpose; }

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
        ApprovalRecord record, 
        String approverId, 
        String approverRole, 
        DecisionType decisionType, 
        String comment, 
        Instant now
    ) {
        if (record.isTerminal()) {
            return Either.left("Request has already reached a terminal state");
        }

        String required = record.requiredAuthority();
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
