package io.effects.recipes.payable;

import io.effects.Either;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PayableRecipeTest {

    // A concrete, behavioral domain request representing a customer payment order.
    // Exposes NO getter APIs for identity or initiators, satisfying our pure OOP guidelines.
    private record CustomerPayment(double maxLimit) implements PayableRequest {

        @Override
        public Either<String, Void> evaluateAuthorization(PaymentLedger ledger, double amount, String currency, Instant now) {
            if (amount <= 0.0) {
                return Either.left("Authorization amount must be positive");
            }
            if (amount > maxLimit) {
                return Either.left("Authorization amount exceeds maximum allowed limit: " + maxLimit);
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateCapture(PaymentLedger ledger, double amount, Instant now) {
            if (amount <= 0.0) {
                return Either.left("Capture amount must be positive");
            }
            // Invariant: cannot capture more than currently remaining authorized amount
            if (amount > ledger.authorizedAmount()) {
                return Either.left("Capture amount " + amount + " exceeds remaining authorized limit: " + ledger.authorizedAmount());
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateReversal(PaymentLedger ledger, Instant now) {
            // Reversal only allowed if not yet captured
            if (ledger.capturedAmount() > 0.0) {
                return Either.left("Cannot reverse: payment has already been partially or fully captured");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateRefund(PaymentLedger ledger, double amount, Instant now) {
            if (amount <= 0.0) {
                return Either.left("Refund amount must be positive");
            }
            double remainingRefundable = ledger.capturedAmount() - ledger.refundedAmount();
            // Invariant: cannot refund more than captured amount
            if (amount > remainingRefundable) {
                return Either.left("Refund amount " + amount + " exceeds remaining refundable limit: " + remainingRefundable);
            }
            return Either.right(null);
        }
    }

    // 1. Initial Authorization Invariant & Limit Validation
    @Test
    void testPaymentAuthorization() {
        PayableProcess process = new PayableProcess();
        CustomerPayment payment = new CustomerPayment(1000.0);
        process.register("pay-1", payment).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T14:00:00Z");

        // Fails because amount is negative
        Either<String, PaymentLedger> badResult1 = process.authorize("pay-1", "user-1", -50.0, "USD", t0).unsafeRunSync();
        assertTrue(badResult1.isLeft());

        // Fails because amount exceeds max limit
        Either<String, PaymentLedger> badResult2 = process.authorize("pay-1", "user-1", 1500.0, "USD", t0).unsafeRunSync();
        assertTrue(badResult2.isLeft());

        // Succeeds
        Either<String, PaymentLedger> successResult = process.authorize("pay-1", "user-1", 500.0, "USD", t0).unsafeRunSync();
        assertTrue(successResult.isRight());
        PaymentLedger ledger = successResult.getRight();

        assertEquals(PaymentLedger.Status.AUTHORIZED, ledger.status());
        assertEquals(500.0, ledger.authorizedAmount());
        assertEquals(0.0, ledger.capturedAmount());
        assertEquals("USD", ledger.currency());

        // Double authorize fails
        Either<String, PaymentLedger> reAuth = process.authorize("pay-1", "user-1", 500.0, "USD", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(reAuth.isLeft());
        assertTrue(reAuth.getLeft().contains("payment already initiated"));
    }

    // 2. Capture & Authorization Bound Verification
    @Test
    void testPaymentCaptureAndBounds() {
        InMemoryStateRepository<String, PaymentLedger> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<PaymentEvent> publisher = new InMemoryEventPublisher<>();
        PayableProcess process = new PayableProcess(repository, publisher, new NoOpTelemetryPort());

        CustomerPayment payment = new CustomerPayment(1000.0);
        process.register("pay-2", payment).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T14:30:00Z");

        // Authorize 500
        process.authorize("pay-2", "user-2", 500.0, "USD", t0).unsafeRunSync();

        // Capture 600 -> Fails (Authorization Bound Invariant: cannot capture more than remaining authorized)
        Either<String, PaymentLedger> badCapture = process.capture("pay-2", "user-2", 600.0, "Charge full amount", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(badCapture.isLeft());
        assertTrue(badCapture.getLeft().contains("exceeds remaining authorized limit"));

        // Capture 300 -> Succeeds (Partial capture)
        Either<String, PaymentLedger> partialCapture1 = process.capture("pay-2", "user-2", 300.0, "Charge part 1", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(partialCapture1.isRight());
        PaymentLedger ledger = partialCapture1.getRight();

        assertEquals(PaymentLedger.Status.CAPTURED, ledger.status());
        assertEquals(200.0, ledger.authorizedAmount()); // 500 - 300 remaining
        assertEquals(300.0, ledger.capturedAmount());

        // Capture 300 more -> Fails (remaining authorized is 200)
        Either<String, PaymentLedger> badCapture2 = process.capture("pay-2", "user-2", 300.0, "Charge part 2", t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(badCapture2.isLeft());

        // Capture remaining 200 -> Succeeds
        Either<String, PaymentLedger> partialCapture2 = process.capture("pay-2", "user-2", 200.0, "Charge remaining", t0.plusSeconds(40)).unsafeRunSync();
        assertTrue(partialCapture2.isRight());
        PaymentLedger finalLedger = partialCapture2.getRight();

        assertEquals(0.0, finalLedger.authorizedAmount());
        assertEquals(500.0, finalLedger.capturedAmount());

        // Verify events
        List<PaymentEvent> events = publisher.getPublishedEvents();
        assertEquals(3, events.size()); // auth, capture 1, capture 2
        assertInstanceOf(PaymentAuthorized.class, events.get(0));
        assertInstanceOf(PaymentCaptured.class, events.get(1));
        assertInstanceOf(PaymentCaptured.class, events.get(2));
    }

    // 3. Reversal Terminal State Invariant
    @Test
    void testReversalLifecycle() {
        PayableProcess process = new PayableProcess();
        CustomerPayment payment = new CustomerPayment(1000.0);
        process.register("pay-3", payment).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T15:00:00Z");

        process.authorize("pay-3", "user-3", 100.0, "USD", t0).unsafeRunSync();

        // Reverse authorization -> Succeeds
        Either<String, PaymentLedger> reverseResult = process.reverse("pay-3", "user-3", "Order cancelled by customer", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(reverseResult.isRight());
        PaymentLedger ledger = reverseResult.getRight();

        assertEquals(PaymentLedger.Status.REVERSED, ledger.status());
        assertEquals(0.0, ledger.authorizedAmount());
        assertTrue(ledger.isTerminal());

        // Try to capture reversed payment -> Fails (State Finality)
        Either<String, PaymentLedger> captureReversed = process.capture("pay-3", "user-3", 50.0, "Force charge", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(captureReversed.isLeft());
    }

    // 4. Refund Bounds & Multi-step Refund Flow
    @Test
    void testRefundInvariants() {
        PayableProcess process = new PayableProcess();
        CustomerPayment payment = new CustomerPayment(1000.0);
        process.register("pay-4", payment).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T15:30:00Z");

        process.authorize("pay-4", "user-4", 200.0, "EUR", t0).unsafeRunSync();
        process.capture("pay-4", "user-4", 200.0, "Capture full", t0.plusSeconds(10)).unsafeRunSync();

        // Refund 250 -> Fails (Refund Bound Invariant: cannot refund more than captured)
        Either<String, PaymentLedger> badRefund = process.refund("pay-4", "user-4", 250.0, "Full refund plus bonus", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(badRefund.isLeft());

        // Refund 50 -> Succeeds (PARTIALLY_REFUNDED)
        Either<String, PaymentLedger> refund1 = process.refund("pay-4", "user-4", 50.0, "Defective item", t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(refund1.isRight());
        PaymentLedger ledger = refund1.getRight();

        assertEquals(PaymentLedger.Status.PARTIALLY_REFUNDED, ledger.status());
        assertEquals(50.0, ledger.refundedAmount());

        // Refund remaining 150 -> Succeeds (REFUNDED, terminal)
        Either<String, PaymentLedger> refund2 = process.refund("pay-4", "user-4", 150.0, "Complete satisfaction guarantee", t0.plusSeconds(40)).unsafeRunSync();
        assertTrue(refund2.isRight());
        PaymentLedger finalLedger = refund2.getRight();

        assertEquals(PaymentLedger.Status.REFUNDED, finalLedger.status());
        assertEquals(200.0, finalLedger.refundedAmount());
        assertTrue(finalLedger.isTerminal());

        // Additional refund fails
        Either<String, PaymentLedger> badRefund2 = process.refund("pay-4", "user-4", 10.0, "Accidental extra", t0.plusSeconds(50)).unsafeRunSync();
        assertTrue(badRefund2.isLeft());
    }
}
