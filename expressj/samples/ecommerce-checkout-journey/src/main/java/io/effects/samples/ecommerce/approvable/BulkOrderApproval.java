package io.effects.samples.ecommerce.approvable;

import io.effects.Either;
import io.effects.recipes.approvable.*;
import java.time.Instant;

public class BulkOrderApproval implements ApprovableRequest<String, String, String> {
    private final double discountPercentage;

    public BulkOrderApproval(double discountPercentage) {
        this.discountPercentage = discountPercentage;
    }

    @Override
    public InitialAssessment<String> evaluateInitialSubmission(Instant now) {
        if (discountPercentage <= 15.0) {
            // Auto-approved
            return new InitialAssessment<>(Status.APPROVED, "NONE");
        } else if (discountPercentage <= 35.0) {
            // Requires Sales Manager
            return new InitialAssessment<>(Status.PENDING, "SALES_MANAGER");
        } else {
            // Requires Sales VP & CFO dual-level (represented sequentially starting with SVP)
            return new InitialAssessment<>(Status.PENDING, "SALES_VP");
        }
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
        if (decisionType == DecisionType.REJECT) {
            return Either.right(new NextStep<>(Status.REJECTED, "NONE"));
        }

        if (decisionType == DecisionType.APPROVE) {
            String currentRequired = record.requiredAuthority();
            if (!currentRequired.equals(approverRole)) {
                return Either.left("Unauthorized: Action requires authority " + currentRequired + " but approver had " + approverRole);
            }

            if ("SALES_MANAGER".equals(currentRequired)) {
                return Either.right(new NextStep<>(Status.APPROVED, "NONE"));
            } else if ("SALES_VP".equals(currentRequired)) {
                // Escalate to CFO for dual approval
                return Either.right(new NextStep<>(Status.PENDING, "CFO"));
            } else if ("CFO".equals(currentRequired)) {
                return Either.right(new NextStep<>(Status.APPROVED, "NONE"));
            }
        }

        if (decisionType == DecisionType.ESCALATE) {
            return Either.right(new NextStep<>(Status.PENDING, comment)); // comment is the target role
        }

        return Either.left("Unsupported decision type");
    }
}
