package io.effects.samples.ecommerce.domain;

import io.effects.Either;
import io.effects.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.EventSubscriber;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.payable.*;
import java.time.Instant;

public class OrderPaymentTransaction implements PayableRequest<String, Double> {
    private final String orderId;
    private final PayableProcess<String, Double> paymentProcess;
    private final EventSubscriber<Object> subscriberPort;
    private final EventPublisher<PaymentEvent<String, Double>> publisherPort;

    public OrderPaymentTransaction(String orderId, EventSubscriber<Object> subscriberPort, EventPublisher<PaymentEvent<String, Double>> publisherPort) {
        this.orderId = orderId;
        this.subscriberPort = subscriberPort;
        this.publisherPort = publisherPort;
        this.paymentProcess = new PayableProcess<>(new InMemoryStateRepository<>(), publisherPort, new NoOpTelemetryPort());
        this.paymentProcess.register(orderId, this).unsafeRunSync();
        if (subscriberPort != null) {
            subscribeToEvents();
        }
    }

    public OrderPaymentTransaction(String orderId) {
        this(orderId, null, new InMemoryEventPublisher<>());
    }

    private void subscribeToEvents() {
        subscriberPort.subscribe("RequestApproved", rawEvent -> IO.delay(() -> {
            if (rawEvent instanceof io.effects.recipes.approvable.RequestApproved<?, ?> event) {
                String ordId = event.requestId().toString();
                Instant now = event.occurredAt();
                
                DomainLogger.info("[CHOREOGRAPHY] OrderPaymentTransaction caught RequestApproved event. Asynchronously authorizing payment for order: " + ordId);
                
                double totalPrice = 50 * 1500.0 * 0.60; // 50 Laptops at 40% discount = $45,000
                // Choreographed side-effect: Auto-authorize the payment transaction!
                authorize("buyer-admin", totalPrice, now.plusSeconds(10));
            }
            return null;
        })).unsafeRunSync();
    }

    public void authorize(String actorId, double amount, Instant time) {
        DomainLogger.info("[PAYMENT] Authorizing purchase amount of $" + amount);
        var res = paymentProcess.authorize(orderId, actorId, amount, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Authorization failed: " + res.getLeft());
        }
        DomainLogger.info("[PAYMENT] Credit authorized. Status: " + res.getRight().status());
    }

    public void capture(String actorId, double amount, String description, Instant time) {
        DomainLogger.info("[PAYMENT] Capturing payment of $" + amount + " against authorized order total...");
        var res = paymentProcess.capture(orderId, actorId, amount, description, time).unsafeRunSync();
        if (res.isLeft()) {
            throw new RuntimeException("Capture failed: " + res.getLeft());
        }
        DomainLogger.info("[PAYMENT] Order capture finalized. Status: " + res.getRight().status() + ". Captured amount: $" + amount);
    }

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
