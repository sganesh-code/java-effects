package io.effects.samples.ecommerce.domain;

import io.effects.ports.EventPublisher;
import io.effects.ports.EventSubscriber;
import io.effects.recipes.negotiable.NegotiationEvent;
import io.effects.recipes.approvable.ApprovalEvent;
import io.effects.recipes.payable.PaymentEvent;
import io.effects.samples.ecommerce.domain.models.BulkOrderTerms;
import java.time.Instant;

/**
 * Collaboration object that coordinates the B2B checkout workflow stages:
 * contract negotiation, multi-level discount approvals, and order payments.
 */
public class CheckoutJourney {
    private final String orderId;
    private final BulkContractNegotiator negotiator;
    private final DiscountApprovalWorkflow approvalWorkflow;
    private final OrderPaymentTransaction paymentTransaction;
    private Order order;

    public CheckoutJourney(
        String orderId, 
        EventSubscriber<Object> subscriberPort,
        EventPublisher<NegotiationEvent<String>> negotiationPublisher,
        EventPublisher<ApprovalEvent<String, String>> approvalPublisher,
        EventPublisher<PaymentEvent<String, Double>> paymentPublisher
    ) {
        this.orderId = orderId;
        this.negotiator = new BulkContractNegotiator(orderId, negotiationPublisher);
        this.approvalWorkflow = new DiscountApprovalWorkflow(orderId, subscriberPort, approvalPublisher);
        this.paymentTransaction = new OrderPaymentTransaction(orderId, subscriberPort, paymentPublisher);
    }

    public CheckoutJourney(String orderId) {
        this.orderId = orderId;
        this.negotiator = new BulkContractNegotiator(orderId);
        this.approvalWorkflow = new DiscountApprovalWorkflow(orderId);
        this.paymentTransaction = new OrderPaymentTransaction(orderId);
    }

    public Order initiateOrder(String itemId, String customerEmail, int quantity, double unitPrice, EventSubscriber<Object> subscriberPort, Warehouse warehouse) {
        this.order = new Order(orderId, itemId, customerEmail, quantity, unitPrice, subscriberPort, warehouse);
        return this.order;
    }

    public Order initiateOrder(String itemId, String customerEmail, int quantity, double unitPrice, EventSubscriber<Object> subscriberPort) {
        return initiateOrder(itemId, customerEmail, quantity, unitPrice, subscriberPort, null);
    }

    public Order initiateOrder(String itemId, String customerEmail, int quantity, double unitPrice) {
        return initiateOrder(itemId, customerEmail, quantity, unitPrice, null, null);
    }

    public void startNegotiation() {
        negotiator.initiate();
    }

    public void proposeTerms(String actorId, BulkOrderTerms terms, Instant time) {
        negotiator.proposeTerms(actorId, terms, time);
    }

    public void counterPropose(String actorId, BulkOrderTerms terms, Instant time) {
        negotiator.counterPropose(actorId, terms, time);
    }

    public void acceptTerms(String actorId, Instant time) {
        negotiator.acceptTerms(actorId, time);
        if (order != null) {
            order.applyNegotiatedDiscount(40.0);
        }
    }

    public void submitForDiscountApproval(String actorId, double discountPercentage, String description, Instant time) {
        approvalWorkflow.submitForDiscountApproval(actorId, discountPercentage, description, time);
    }

    public void approveDiscount(String approverId, String role, String comment, Instant time) {
        approvalWorkflow.approveDiscount(approverId, role, comment, time);
    }

    public void authorizePayment(String actorId, double amount, Instant time) {
        paymentTransaction.authorize(actorId, amount, time);
    }

    public void capturePayment(String actorId, double amount, String description, Instant time) {
        paymentTransaction.capture(actorId, amount, description, time);
    }
}
