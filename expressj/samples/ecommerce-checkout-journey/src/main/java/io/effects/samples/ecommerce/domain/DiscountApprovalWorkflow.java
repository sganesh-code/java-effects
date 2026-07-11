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

/**
 * Represents a corporate purchase discount approval workflow. 
 * It evaluates pricing discount requests and escalates approvals to Sales VPs or CFOs based on volume terms.
 */
public class DiscountApprovalWorkflow implements ApprovableRequest<String, String, String> {
    private final String orderId;
    private final ApprovalProcess<String, String, String> approvalProcess;
    private final EventSubscriber<Object> subscriberPort;
    private final EventPublisher<ApprovalEvent<String, String>> publisherPort;
    private double discountPercentage;

    /**
     * Initializes a corporate discount approval workflow linked to a unique purchase order ID.
     */
    public DiscountApprovalWorkflow(String orderId, EventSubscriber<Object> subscriberPort, EventPublisher<ApprovalEvent<String, String>> publisherPort) {
        this.orderId = orderId;
        this.subscriberPort = subscriberPort;
        this.publisherPort = publisherPort;
        this.approvalProcess = new ApprovalProcess<>(new InMemoryStateRepository<>(), publisherPort, new NoOpTelemetryPort());
        if (subscriberPort != null) {
            setupApprovalTriggers();
        }
    }

    public DiscountApprovalWorkflow(String orderId) {
        this(orderId, null, new InMemoryEventPublisher<>());
    }

    /**
     * Configures automatic approval triggers to execute executive decision-making reviews 
     * when high-volume contracts are submitted.
     */
    private void setupApprovalTriggers() {
        subscriberPort.subscribe("RequestSubmitted", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.approvable.RequestSubmitted<?, ?> event) {
                String ordId = event.requestId().toString();
                String currentRequired = event.requiredAuthority().toString();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[APPROVAL] Executive review ticket submitted. Automatically evaluating policies for authority role: " + currentRequired);
                
                if ("SALES_VP".equals(currentRequired)) {
                    // Automated Sales VP Sarah automatically approves strategic high-volume contracts
                    approveDiscount("vp-sarah", "SALES_VP", "Approved volume discount - strategic corporate account.", now.plusSeconds(5));
                } else if ("CFO".equals(currentRequired)) {
                    // Automated CFO Steve automatically approves corporate finance requirements
                    approveDiscount("cfo-steve", "CFO", "Financially approved via automated corporate CFO policy.", now.plusSeconds(5));
                }
            }
            return null;
        })).unsafeRunSync();
    }

    // --- Core Approval Operations ---

    /**
     * Submits a negotiated contract discount percentage for official executive reviews.
     */
    public void submitForDiscountApproval(String actorId, double discountPercentage, String description, Instant time) {
        DomainLogger.info("[APPROVAL] Discount of " + discountPercentage + "% exceeds standard auto-approval threshold (15%). Submitting for Sales VP & CFO reviews...");
        this.discountPercentage = discountPercentage;
        approvalProcess.register(orderId, this).unsafeRunSync();
        var res = approvalProcess.submit(orderId, actorId, description, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Approval submission failed: " + res.getLeft());
        }
        DomainLogger.info("[APPROVAL] Review ticket successfully submitted. Status: " + res.getRight().status() + ". Next pending reviewer: " + res.getRight().requiredAuthority());
    }

    /**
     * Registers an executive approval decision on a pending discount request.
     */
    public void approveDiscount(String approverId, String role, String comment, Instant time) {
        var res = approvalProcess.approve(orderId, approverId, role, comment, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Executive approval failed: " + res.getLeft());
        }
        ApprovalRecord<String, String, String> record = res.getRight();
        if (record.status() == Status.APPROVED) {
            DomainLogger.info("[APPROVAL] Final CFO Approval cleared! Checkout discount has been fully authorized.");
        } else {
            DomainLogger.info("[APPROVAL] " + role + " Approved. Ticket escalated to next level. Pending: " + record.requiredAuthority());
        }
    }

    // --- Internal Business Invariants & Policies ---

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
                return Either.left("Review policy error: Action requires authority level " + currentRequired + " but approver had " + approverRole);
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
