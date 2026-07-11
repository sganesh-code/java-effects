package io.effects.samples.ecommerce.domain;

import io.effects.Either;
import io.effects.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.EventSubscriber;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.approvable.*;
import java.time.Instant;

public class DiscountApprovalWorkflow implements ApprovableRequest<String, String, String> {
    private final String orderId;
    private final ApprovalProcess<String, String, String> approvalProcess;
    private final EventSubscriber<Object> subscriberPort;
    private final EventPublisher<ApprovalEvent<String, String>> publisherPort;
    private double discountPercentage;

    public DiscountApprovalWorkflow(String orderId, EventSubscriber<Object> subscriberPort, EventPublisher<ApprovalEvent<String, String>> publisherPort) {
        this.orderId = orderId;
        this.subscriberPort = subscriberPort;
        this.publisherPort = publisherPort;
        this.approvalProcess = new ApprovalProcess<>(new InMemoryStateRepository<>(), publisherPort, new NoOpTelemetryPort());
        if (subscriberPort != null) {
            subscribeToEvents();
        }
    }

    public DiscountApprovalWorkflow(String orderId) {
        this(orderId, null, new InMemoryEventPublisher<>());
    }

    private void subscribeToEvents() {
        // RequestSubmitted -> Auto approve (Sarah and steve robots)
        subscriberPort.subscribe("RequestSubmitted", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.approvable.RequestSubmitted<?, ?> event) {
                String ordId = event.requestId().toString();
                String currentRequired = event.requiredAuthority().toString();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[CHOREOGRAPHY] DiscountApprovalWorkflow caught RequestSubmitted. Asynchronously applying decision for role: " + currentRequired);
                
                if ("SALES_VP".equals(currentRequired)) {
                    // Automated VP Sarah approves strategic corporate accounts
                    approveDiscount("vp-sarah", "SALES_VP", "Approved volume discount - strategic corporate account.", now.plusSeconds(5));
                } else if ("CFO".equals(currentRequired)) {
                    // Automated CFO Steve approves
                    approveDiscount("cfo-steve", "CFO", "Financially approved via automated corporate CFO policy.", now.plusSeconds(5));
                }
            }
            return null;
        })).unsafeRunSync();
    }

    public void submitForDiscountApproval(String actorId, double discountPercentage, String description, Instant time) {
        DomainLogger.info("[APPROVAL] Discount of " + discountPercentage + "% exceeds standard auto-approval (15%). Submitting for VP & CFO approvals.");
        this.discountPercentage = discountPercentage;
        approvalProcess.register(orderId, this).unsafeRunSync();
        var res = approvalProcess.submit(orderId, actorId, description, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Submit failed: " + res.getLeft());
        }
        DomainLogger.info("[APPROVAL] Submitted. Current status: " + res.getRight().status() + ". Required: " + res.getRight().requiredAuthority());
    }

    public void approveDiscount(String approverId, String role, String comment, Instant time) {
        var res = approvalProcess.approve(orderId, approverId, role, comment, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Approval failed: " + res.getLeft());
        }
        ApprovalRecord<String, String, String> record = res.getRight();
        if (record.status() == Status.APPROVED) {
            DomainLogger.info("[APPROVAL] CFO Approved! Final status: " + record.status());
        } else {
            DomainLogger.info("[APPROVAL] " + role + " Approved. Status escalated. Next required: " + record.requiredAuthority());
        }
    }

    @Override
    public InitialAssessment<String> evaluateInitialSubmission(Instant now) {
        if (discountPercentage <= 15.0) {
            return new InitialAssessment<>(Status.APPROVED, "NONE");
        } else if (discountPercentage <= 35.0) {
            return new InitialAssessment<>(Status.PENDING, "SALES_MANAGER");
        } else {
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
                return Either.right(new NextStep<>(Status.PENDING, "CFO"));
            } else if ("CFO".equals(currentRequired)) {
                return Either.right(new NextStep<>(Status.APPROVED, "NONE"));
            }
        }

        if (decisionType == DecisionType.ESCALATE) {
            return Either.right(new NextStep<>(Status.PENDING, comment));
        }

        return Either.left("Unsupported decision type");
    }
}
