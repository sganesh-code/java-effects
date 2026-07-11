package io.effects.samples.ecommerce.domain;

import io.effects.ports.EventPublisher;
import io.effects.ports.EventSubscriber;
import io.effects.recipes.negotiable.NegotiationEvent;
import io.effects.recipes.approvable.ApprovalEvent;
import io.effects.recipes.payable.PaymentEvent;
import io.effects.samples.ecommerce.domain.models.BulkOrderTerms;
import java.time.Instant;

/**
 * Coordinates the commercial B2B checkout workflow. 
 * It manages contract price negotiation, corporate discount approval workflows, and order payment processing.
 */
public class Checkout {
    private final BulkContractNegotiator negotiator;
    private final DiscountApprovalWorkflow approvalWorkflow;
    private final Payment payment;
    private Order order;

    /**
     * Creates a new B2B checkout coordinator linked to a unique purchase order ID, 
     * utilizing shared channels for message communications.
     */
    public Checkout(
        String orderId, 
        EventSubscriber<Object> subscriberPort,
        EventPublisher<NegotiationEvent<String>> negotiationPublisher,
        EventPublisher<ApprovalEvent<String, String>> approvalPublisher,
        EventPublisher<PaymentEvent<String, Double>> paymentPublisher
    ) {
        this.negotiator = new BulkContractNegotiator(orderId, negotiationPublisher);
        this.approvalWorkflow = new DiscountApprovalWorkflow(orderId, subscriberPort, approvalPublisher);
        this.payment = new Payment(orderId, subscriberPort, paymentPublisher);
    }

    /**
     * Initializes a customer purchase order and registers it with the checkout coordinator.
     */
    public Order initiateOrder(String itemId, String customerEmail, int quantity, double unitPrice, EventSubscriber<Object> subscriberPort, Warehouse warehouse) {
        this.order = new Order(itemId, customerEmail, quantity, unitPrice, subscriberPort, warehouse);
        return this.order;
    }

    /**
     * Begins commercial price negotiation for a bulk order.
     */
    public void startNegotiation() {
        negotiator.initiate();
    }

    /**
     * Proposes bulk purchase volume and unit pricing terms on behalf of the buyer.
     */
    public void proposeTerms(String actorId, BulkOrderTerms terms, Instant time) {
        negotiator.proposeTerms(actorId, terms, time);
    }

    /**
     * Counter-proposes adjusted discount and volume pricing terms on behalf of the seller.
     */
    public void counterPropose(String actorId, BulkOrderTerms terms, Instant time) {
        negotiator.counterPropose(actorId, terms, time);
    }

    /**
     * Accepts finalized negotiation terms, updating the order value and automatically 
     * submitting the bulk volume discount for corporate approval reviews.
     */
    public void acceptTerms(String actorId, Instant time) {
        negotiator.acceptTerms(actorId, time);
        if (order != null) {
            order.applyNegotiatedDiscount(40.0);
        }
        
        // Accepting negotiated contract terms automatically triggers executive review submission
        submitForDiscountApproval("sales-rep", 40.0, "Bulk contract for 50 Laptops at 40% discount", time.plusSeconds(10));
    }

    /**
     * Submits a contract discount for authorized executive review.
     */
    public void submitForDiscountApproval(String actorId, double discountPercentage, String description, Instant time) {
        approvalWorkflow.submitForDiscountApproval(actorId, discountPercentage, description, time);
    }

    /**
     * Captures and settles the finalized purchase payment amount.
     */
    public void capturePayment(String actorId, double amount, String description, Instant time) {
        payment.capture(actorId, amount, description, time);
    }
}
