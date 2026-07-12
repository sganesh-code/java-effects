package io.effects.samples.ecommerce.domain;

import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.EventSubscriber;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.payable.*;
import io.effects.recipes.payable.models.*;
import java.time.Instant;

/**
 * Represents a purchase transaction payment handler. 
 * It manages buyer account credit card pre-authorizations, final billing captures, and refund transactions.
 */
public class Payment implements PayableRequest<String, Double> {
    private final String orderId;
    private final PayableProcess<String, Double> paymentProcess;
    private final EventSubscriber<Object> subscriberPort;

    /**
     * Initializes a payment transaction coordinator linked to a unique purchase order ID.
     */
    public Payment(String orderId, EventSubscriber<Object> subscriberPort, EventPublisher<PaymentEvent<String, Double>> publisherPort) {
        this.orderId = orderId;
        this.subscriberPort = subscriberPort;
        this.paymentProcess = new PayableProcess<>(new InMemoryStateRepository<>(), publisherPort, new NoOpTelemetryPort());
        this.paymentProcess.register(orderId, this).unsafeRunSync();
        if (subscriberPort != null) {
            setupPaymentTriggers();
        }
    }

    public Payment(String orderId) {
        this(orderId, null, new InMemoryEventPublisher<>());
    }

    /**
     * Configures automatic triggers to execute credit authorizations when preceding discount 
     * approval checks successfully clear.
     */
    private void setupPaymentTriggers() {
        subscriberPort.subscribe("RequestApproved", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.approvable.models.RequestApproved<?, ?> event) {
                String ordId = event.requestId().toString();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[PAYMENT] Corporate discount approval cleared successfully. Automatically pre-authorizing credit for order: " + ordId);
                
                double totalPrice = 50 * 1500.0 * 0.60; // 50 Laptops at 40% discount = $45,000
                authorize("buyer-admin", totalPrice, now.plusSeconds(10));
            }
            return null;
        })).unsafeRunSync();
    }

    // --- Core Payment Operations ---

    /**
     * Pre-authorizes buyer credit, reserving the purchase funds on their corporate account.
     */
    public void authorize(String actorId, double amount, Instant time) {
        DomainLogger.info("[PAYMENT] Pre-authorizing purchase amount of $" + amount + "...");
        var res = paymentProcess.authorize(orderId, actorId, amount, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Credit pre-authorization failed: " + res.getLeft());
        }
        DomainLogger.info("[PAYMENT] Credit successfully pre-authorized! Ledger status: " + res.getRight().status());
    }

    /**
     * Settles the billing transaction, capturing the pre-authorized funds and finalizing the sale.
     */
    public void capture(String actorId, double amount, String description, Instant time) {
        DomainLogger.info("[PAYMENT] Dispatch completed. Capturing $" + amount + " against corporate credit authorization...");
        var res = paymentProcess.capture(orderId, actorId, amount, description, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Payment capture failed: " + res.getLeft());
        }
        DomainLogger.info("[PAYMENT] Settle-capture finalized. Transaction status: " + res.getRight().status() + ". Settle amount: $" + amount);
    }

    // --- Internal Business Invariants & Policies ---

    @Override
    public Either<String, Void> evaluateAuthorization(PaymentLedger<String, Double> ledger, Double amount, Instant now) {
        if (amount <= 0.0) {
            return Either.left("Payment authorization amount must be positive.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, Void> evaluateCapture(PaymentLedger<String, Double> ledger, Double amount, Instant now) {
        if (amount <= 0.0) {
            return Either.left("Payment capture amount must be positive.");
        }
        return Either.right(null);
    }

    @Override
    public Either<String, PaymentLedger.Status> evaluateRefund(PaymentLedger<String, Double> ledger, Double amount, Instant now) {
        if (amount <= 0.0) {
            return Either.left("Refund amount must be positive.");
        }
        return Either.right(PaymentLedger.Status.REFUNDED);
    }

    @Override
    public Either<String, Void> evaluateReversal(PaymentLedger<String, Double> ledger, Instant now) {
        return Either.right(null);
    }
}
