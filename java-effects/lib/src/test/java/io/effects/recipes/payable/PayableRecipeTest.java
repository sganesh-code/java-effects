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

    // A custom, clean, non-anemic domain representation of Money.
    private record Money(double amount, String currency) {
        public Money add(Money other) {
            if (!this.currency.equals(other.currency)) {
                throw new IllegalArgumentException("Currency mismatch");
            }
            return new Money(this.amount + other.amount, this.currency);
        }

        public Money subtract(Money other) {
            if (!this.currency.equals(other.currency)) {
                throw new IllegalArgumentException("Currency mismatch");
            }
            return new Money(this.amount - other.amount, this.currency);
        }

        public boolean isGreaterThan(Money other) {
            if (!this.currency.equals(other.currency)) {
                throw new IllegalArgumentException("Currency mismatch");
            }
            return this.amount > other.amount;
        }
    }

    // A concrete, behavioral domain request representing a customer payment order.
    // Exposes NO getter APIs for identity or initiators, satisfying our pure OOP guidelines.
    private record CustomerPayment(double maxLimit) implements PayableRequest<String, Money> {

        private Money calculateRemainingAuthorized(PaymentLedger<String, Money> ledger) {
            double auth = ledger.history().stream()
                .filter(s -> s.type() == PaymentStep.Type.AUTHORIZE)
                .mapToDouble(s -> s.detail().amount())
                .sum();
            double captured = ledger.history().stream()
                .filter(s -> s.type() == PaymentStep.Type.CAPTURE)
                .mapToDouble(s -> s.detail().amount())
                .sum();
            String currency = ledger.history().stream()
                .map(s -> s.detail().currency())
                .findFirst()
                .orElse("USD");
            return new Money(auth - captured, currency);
        }

        private Money calculateTotalCaptured(PaymentLedger<String, Money> ledger) {
            double captured = ledger.history().stream()
                .filter(s -> s.type() == PaymentStep.Type.CAPTURE)
                .mapToDouble(s -> s.detail().amount())
                .sum();
            String currency = ledger.history().stream()
                .map(s -> s.detail().currency())
                .findFirst()
                .orElse("USD");
            return new Money(captured, currency);
        }

        private Money calculateTotalRefunded(PaymentLedger<String, Money> ledger) {
            double refunded = ledger.history().stream()
                .filter(s -> s.type() == PaymentStep.Type.REFUND)
                .mapToDouble(s -> s.detail().amount())
                .sum();
            String currency = ledger.history().stream()
                .map(s -> s.detail().currency())
                .findFirst()
                .orElse("USD");
            return new Money(refunded, currency);
        }

        @Override
        public Either<String, Void> evaluateAuthorization(PaymentLedger<String, Money> ledger, Money detail, Instant now) {
            if (detail.amount() <= 0.0) {
                return Either.left("Authorization amount must be positive");
            }
            if (detail.amount() > maxLimit) {
                return Either.left("Authorization amount exceeds maximum allowed limit: " + maxLimit);
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateCapture(PaymentLedger<String, Money> ledger, Money detail, Instant now) {
            if (detail.amount() <= 0.0) {
                return Either.left("Capture amount must be positive");
            }
            Money remainingAuthorized = calculateRemainingAuthorized(ledger);
            if (detail.isGreaterThan(remainingAuthorized)) {
                return Either.left("Capture amount " + detail.amount() + " exceeds remaining authorized limit: " + remainingAuthorized.amount());
            }
            return Either.right(null);
        }

        @Override
        public Either<String, Void> evaluateReversal(PaymentLedger<String, Money> ledger, Instant now) {
            boolean hasCapture = ledger.history().stream()
                .anyMatch(s -> s.type() == PaymentStep.Type.CAPTURE);
            if (hasCapture) {
                return Either.left("Cannot reverse: payment has already been partially or fully captured");
            }
            return Either.right(null);
        }

        @Override
        public Either<String, PaymentLedger.Status> evaluateRefund(PaymentLedger<String, Money> ledger, Money detail, Instant now) {
            if (detail.amount() <= 0.0) {
                return Either.left("Refund amount must be positive");
            }
            Money captured = calculateTotalCaptured(ledger);
            Money refunded = calculateTotalRefunded(ledger);
            Money remainingRefundable = captured.subtract(refunded);

            if (detail.isGreaterThan(remainingRefundable)) {
                return Either.left("Refund amount " + detail.amount() + " exceeds remaining refundable limit: " + remainingRefundable.amount());
            }

            PaymentLedger.Status nextStatus = (detail.amount() >= remainingRefundable.amount())
                ? PaymentLedger.Status.REFUNDED
                : PaymentLedger.Status.PARTIALLY_REFUNDED;
            return Either.right(nextStatus);
        }
    }

    // Helpers to verify totals inside tests by reading history (since ledger has no getters)
    private static double getAuthorizedSum(PaymentLedger<String, Money> ledger) {
        return ledger.history().stream()
            .filter(s -> s.type() == PaymentStep.Type.AUTHORIZE)
            .mapToDouble(s -> s.detail().amount())
            .sum();
    }

    private static double getCapturedSum(PaymentLedger<String, Money> ledger) {
        return ledger.history().stream()
            .filter(s -> s.type() == PaymentStep.Type.CAPTURE)
            .mapToDouble(s -> s.detail().amount())
            .sum();
    }

    private static double getRefundedSum(PaymentLedger<String, Money> ledger) {
        return ledger.history().stream()
            .filter(s -> s.type() == PaymentStep.Type.REFUND)
            .mapToDouble(s -> s.detail().amount())
            .sum();
    }

    private static String getCurrency(PaymentLedger<String, Money> ledger) {
        return ledger.history().stream()
            .map(s -> s.detail().currency())
            .findFirst()
            .orElse("USD");
    }

    // 1. Initial Authorization Invariant & Limit Validation
    @Test
    void testPaymentAuthorization() {
        PayableProcess<String, Money> process = new PayableProcess<>();
        CustomerPayment payment = new CustomerPayment(1000.0);
        process.register("pay-1", payment).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T14:00:00Z");

        // Fails because amount is negative
        Either<String, PaymentLedger<String, Money>> badResult1 = process.authorize("pay-1", "user-1", new Money(-50.0, "USD"), t0).unsafeRunSync();
        assertTrue(badResult1.isLeft());

        // Fails because amount exceeds max limit
        Either<String, PaymentLedger<String, Money>> badResult2 = process.authorize("pay-1", "user-1", new Money(1500.0, "USD"), t0).unsafeRunSync();
        assertTrue(badResult2.isLeft());

        // Succeeds
        Either<String, PaymentLedger<String, Money>> successResult = process.authorize("pay-1", "user-1", new Money(500.0, "USD"), t0).unsafeRunSync();
        assertTrue(successResult.isRight());
        PaymentLedger<String, Money> ledger = successResult.getRight();

        assertEquals(PaymentLedger.Status.AUTHORIZED, ledger.status());
        assertEquals(500.0, getAuthorizedSum(ledger));
        assertEquals(0.0, getCapturedSum(ledger));
        assertEquals("USD", getCurrency(ledger));

        // Double authorize fails
        Either<String, PaymentLedger<String, Money>> reAuth = process.authorize("pay-1", "user-1", new Money(500.0, "USD"), t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(reAuth.isLeft());
        assertTrue(reAuth.getLeft().contains("payment already initiated"));
    }

    // 2. Capture & Authorization Bound Verification
    @Test
    void testPaymentCaptureAndBounds() {
        InMemoryStateRepository<String, PaymentLedger<String, Money>> repository = new InMemoryStateRepository<>();
        InMemoryEventPublisher<PaymentEvent<String, Money>> publisher = new InMemoryEventPublisher<>();
        PayableProcess<String, Money> process = new PayableProcess<>(repository, publisher, new NoOpTelemetryPort());

        CustomerPayment payment = new CustomerPayment(1000.0);
        process.register("pay-2", payment).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T14:30:00Z");

        // Authorize 500
        process.authorize("pay-2", "user-2", new Money(500.0, "USD"), t0).unsafeRunSync();

        // Capture 600 -> Fails (Authorization Bound Invariant: cannot capture more than remaining authorized)
        Either<String, PaymentLedger<String, Money>> badCapture = process.capture("pay-2", "user-2", new Money(600.0, "USD"), "Charge full amount", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(badCapture.isLeft());
        assertTrue(badCapture.getLeft().contains("exceeds remaining authorized limit"));

        // Capture 300 -> Succeeds (Partial capture)
        Either<String, PaymentLedger<String, Money>> partialCapture1 = process.capture("pay-2", "user-2", new Money(300.0, "USD"), "Charge part 1", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(partialCapture1.isRight());
        PaymentLedger<String, Money> ledger = partialCapture1.getRight();

        assertEquals(PaymentLedger.Status.CAPTURED, ledger.status());
        assertEquals(300.0, getCapturedSum(ledger));

        // Capture 300 more -> Fails (remaining authorized is 200)
        Either<String, PaymentLedger<String, Money>> badCapture2 = process.capture("pay-2", "user-2", new Money(300.0, "USD"), "Charge part 2", t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(badCapture2.isLeft());

        // Capture remaining 200 -> Succeeds
        Either<String, PaymentLedger<String, Money>> partialCapture2 = process.capture("pay-2", "user-2", new Money(200.0, "USD"), "Charge remaining", t0.plusSeconds(40)).unsafeRunSync();
        assertTrue(partialCapture2.isRight());
        PaymentLedger<String, Money> finalLedger = partialCapture2.getRight();

        assertEquals(500.0, getCapturedSum(finalLedger));

        // Verify events
        List<PaymentEvent<String, Money>> events = publisher.getPublishedEvents();
        assertEquals(3, events.size()); // auth, capture 1, capture 2
        assertInstanceOf(PaymentAuthorized.class, events.get(0));
        assertInstanceOf(PaymentCaptured.class, events.get(1));
        assertInstanceOf(PaymentCaptured.class, events.get(2));
    }

    // 3. Reversal Terminal State Invariant
    @Test
    void testReversalLifecycle() {
        PayableProcess<String, Money> process = new PayableProcess<>();
        CustomerPayment payment = new CustomerPayment(1000.0);
        process.register("pay-3", payment).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T15:00:00Z");

        process.authorize("pay-3", "user-3", new Money(100.0, "USD"), t0).unsafeRunSync();

        // Reverse authorization -> Succeeds
        Either<String, PaymentLedger<String, Money>> reverseResult = process.reverse("pay-3", "user-3", "Order cancelled by customer", t0.plusSeconds(10)).unsafeRunSync();
        assertTrue(reverseResult.isRight());
        PaymentLedger<String, Money> ledger = reverseResult.getRight();

        assertEquals(PaymentLedger.Status.REVERSED, ledger.status());
        assertTrue(ledger.isTerminal());

        // Try to capture reversed payment -> Fails (State Finality)
        Either<String, PaymentLedger<String, Money>> captureReversed = process.capture("pay-3", "user-3", new Money(50.0, "USD"), "Force charge", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(captureReversed.isLeft());
    }

    // 4. Refund Bounds & Multi-step Refund Flow
    @Test
    void testRefundInvariants() {
        PayableProcess<String, Money> process = new PayableProcess<>();
        CustomerPayment payment = new CustomerPayment(1000.0);
        process.register("pay-4", payment).unsafeRunSync();

        Instant t0 = Instant.parse("2026-07-08T15:30:00Z");

        process.authorize("pay-4", "user-4", new Money(200.0, "EUR"), t0).unsafeRunSync();
        process.capture("pay-4", "user-4", new Money(200.0, "EUR"), "Capture full", t0.plusSeconds(10)).unsafeRunSync();

        // Refund 250 -> Fails (Refund Bound Invariant: cannot refund more than captured)
        Either<String, PaymentLedger<String, Money>> badRefund = process.refund("pay-4", "user-4", new Money(250.0, "EUR"), "Full refund plus bonus", t0.plusSeconds(20)).unsafeRunSync();
        assertTrue(badRefund.isLeft());

        // Refund 50 -> Succeeds (PARTIALLY_REFUNDED)
        Either<String, PaymentLedger<String, Money>> refund1 = process.refund("pay-4", "user-4", new Money(50.0, "EUR"), "Defective item", t0.plusSeconds(30)).unsafeRunSync();
        assertTrue(refund1.isRight());
        PaymentLedger<String, Money> ledger = refund1.getRight();

        assertEquals(PaymentLedger.Status.PARTIALLY_REFUNDED, ledger.status());
        assertEquals(50.0, getRefundedSum(ledger));

        // Refund remaining 150 -> Succeeds (REFUNDED, terminal)
        Either<String, PaymentLedger<String, Money>> refund2 = process.refund("pay-4", "user-4", new Money(150.0, "EUR"), "Complete satisfaction guarantee", t0.plusSeconds(40)).unsafeRunSync();
        assertTrue(refund2.isRight());
        PaymentLedger<String, Money> finalLedger = refund2.getRight();

        assertEquals(PaymentLedger.Status.REFUNDED, finalLedger.status());
        assertEquals(200.0, getRefundedSum(finalLedger));
        assertTrue(finalLedger.isTerminal());

        // Additional refund fails
        Either<String, PaymentLedger<String, Money>> badRefund2 = process.refund("pay-4", "user-4", new Money(10.0, "EUR"), "Accidental extra", t0.plusSeconds(50)).unsafeRunSync();
        assertTrue(badRefund2.isLeft());
    }
}