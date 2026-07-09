package io.effects.recipes.payable;

import io.effects.Either;
import io.effects.IO;
import io.effects.ForIO;
import io.effects.ports.EventPublisher;
import io.effects.ports.StateRepository;
import io.effects.ports.TelemetryPort;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An Object-Oriented "Recipe" representing a Payment Process Manager.
 * It coordinates routing messages, evaluating domain invariants, persistence, event emission, and telemetry.
 * 
 * In accordance with our architectural boundary, this process represents the monadic infrastructure
 * engine, and thus exposes purely monadic APIs (returning IO) to allow lazy, virtual-thread execution,
 * cancellation, and pipeline composition.
 */
public final class PayableProcess {
    private final StateRepository<String, PaymentLedger> repository;
    private final EventPublisher<PaymentEvent> publisher;
    private final TelemetryPort telemetry;
    private final ConcurrentMap<String, PayableRequest> payments = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public PayableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public PayableProcess(
        StateRepository<String, PaymentLedger> repository,
        EventPublisher<PaymentEvent> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    /**
     * Registers a behavioral payment request domain object.
     */
    public IO<Void> register(String paymentId, PayableRequest payment) {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(payment);
        return IO.delay(() -> {
            payments.put(paymentId, payment);
            return null;
        });
    }

    /**
     * Authorizes an initial payment amount.
     */
    public IO<Either<String, PaymentLedger>> authorize(String paymentId, String actorId, double amount, String currency, Instant now) {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(currency);
        Objects.requireNonNull(now);

        PayableRequest payment = payments.get(paymentId);
        if (payment == null) {
            return IO.of(Either.left("Payment domain object not registered: " + paymentId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(paymentId))
            .bind((startTime, optRecord) -> {
                PaymentLedger ledger = optRecord.orElseGet(() -> new PaymentLedger(paymentId));

                if (ledger.status() != PaymentLedger.Status.INITIAL) {
                    return IO.of(Either.<String, PaymentLedger>left("Cannot authorize: payment already initiated (current status: " + ledger.status() + ")"));
                }

                // Invoke pure domain double dispatch validation synchronously
                Either<String, Void> eitherValid = payment.evaluateAuthorization(ledger, amount, currency, now);
                if (eitherValid.isLeft()) {
                    return IO.of(Either.<String, PaymentLedger>left(eitherValid.getLeft()));
                }

                PaymentStep step = new PaymentStep(
                    UUID.randomUUID().toString(),
                    actorId,
                    PaymentStep.Type.AUTHORIZE,
                    amount,
                    "Payment authorized",
                    now
                );
                ledger.recordStep(step, PaymentLedger.Status.AUTHORIZED, amount, 0.0, 0.0, currency);

                PaymentEvent event = new PaymentAuthorized(paymentId, amount, currency, now);

                return repository.save(paymentId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("payable", paymentId + ":authorize"))
                    .flatMap(v -> telemetry.recordDuration("payable", paymentId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, PaymentLedger>right(ledger));
            })
            .yield((startTime, optRecord, result) -> result);
    }

    /**
     * Captures an authorized payment.
     */
    public IO<Either<String, PaymentLedger>> capture(String paymentId, String actorId, double amount, String comment, Instant now) {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        PayableRequest payment = payments.get(paymentId);
        if (payment == null) {
            return IO.of(Either.left("Payment domain object not registered: " + paymentId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(paymentId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isEmpty()) {
                    return IO.of(Either.<String, PaymentLedger>left("Payment ledger not found: " + paymentId));
                }

                PaymentLedger ledger = optRecord.get();
                if (ledger.status() != PaymentLedger.Status.AUTHORIZED && ledger.status() != PaymentLedger.Status.CAPTURED) {
                    return IO.of(Either.<String, PaymentLedger>left("Cannot capture payment in current status: " + ledger.status()));
                }

                // Invoke pure domain double dispatch validation synchronously
                Either<String, Void> eitherValid = payment.evaluateCapture(ledger, amount, now);
                if (eitherValid.isLeft()) {
                    return IO.of(Either.<String, PaymentLedger>left(eitherValid.getLeft()));
                }

                PaymentStep step = new PaymentStep(
                    UUID.randomUUID().toString(),
                    actorId,
                    PaymentStep.Type.CAPTURE,
                    amount,
                    comment,
                    now
                );
                // Reduce authorized amount by captured amount, and increase captured amount
                ledger.recordStep(step, PaymentLedger.Status.CAPTURED, -amount, amount, 0.0, null);

                PaymentEvent event = new PaymentCaptured(paymentId, amount, now);

                return repository.save(paymentId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("payable", paymentId + ":capture"))
                    .flatMap(v -> telemetry.recordDuration("payable", paymentId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, PaymentLedger>right(ledger));
            })
            .yield((startTime, optRecord, result) -> result);
    }

    /**
     * Reverses/voids an active payment authorization.
     */
    public IO<Either<String, PaymentLedger>> reverse(String paymentId, String actorId, String reason, Instant now) {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);

        PayableRequest payment = payments.get(paymentId);
        if (payment == null) {
            return IO.of(Either.left("Payment domain object not registered: " + paymentId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(paymentId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isEmpty()) {
                    return IO.of(Either.<String, PaymentLedger>left("Payment ledger not found: " + paymentId));
                }

                PaymentLedger ledger = optRecord.get();
                if (ledger.status() != PaymentLedger.Status.AUTHORIZED) {
                    return IO.of(Either.<String, PaymentLedger>left("Cannot reverse: payment status is not AUTHORIZED (current status: " + ledger.status() + ")"));
                }

                // Invoke pure domain double dispatch validation synchronously
                Either<String, Void> eitherValid = payment.evaluateReversal(ledger, now);
                if (eitherValid.isLeft()) {
                    return IO.of(Either.<String, PaymentLedger>left(eitherValid.getLeft()));
                }

                PaymentStep step = new PaymentStep(
                    UUID.randomUUID().toString(),
                    actorId,
                    PaymentStep.Type.REVERSE,
                    ledger.authorizedAmount(),
                    reason,
                    now
                );
                double originalAuth = ledger.authorizedAmount();
                ledger.recordStep(step, PaymentLedger.Status.REVERSED, -originalAuth, 0.0, 0.0, null);

                PaymentEvent event = new PaymentReversed(paymentId, now);

                return repository.save(paymentId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("payable", paymentId + ":reverse"))
                    .flatMap(v -> telemetry.recordDuration("payable", paymentId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, PaymentLedger>right(ledger));
            })
            .yield((startTime, optRecord, result) -> result);
    }

    /**
     * Refunds a captured payment.
     */
    public IO<Either<String, PaymentLedger>> refund(String paymentId, String actorId, double amount, String reason, Instant now) {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);

        PayableRequest payment = payments.get(paymentId);
        if (payment == null) {
            return IO.of(Either.left("Payment domain object not registered: " + paymentId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(paymentId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isEmpty()) {
                    return IO.of(Either.<String, PaymentLedger>left("Payment ledger not found: " + paymentId));
                }

                PaymentLedger ledger = optRecord.get();
                if (ledger.status() != PaymentLedger.Status.CAPTURED && ledger.status() != PaymentLedger.Status.PARTIALLY_REFUNDED) {
                    return IO.of(Either.<String, PaymentLedger>left("Cannot refund payment in current status: " + ledger.status()));
                }

                // Invoke pure domain double dispatch validation synchronously
                Either<String, Void> eitherValid = payment.evaluateRefund(ledger, amount, now);
                if (eitherValid.isLeft()) {
                    return IO.of(Either.<String, PaymentLedger>left(eitherValid.getLeft()));
                }

                PaymentStep step = new PaymentStep(
                    UUID.randomUUID().toString(),
                    actorId,
                    PaymentStep.Type.REFUND,
                    amount,
                    reason,
                    now
                );

                double nextRefunded = ledger.refundedAmount() + amount;
                PaymentLedger.Status nextStatus = nextRefunded >= ledger.capturedAmount() 
                    ? PaymentLedger.Status.REFUNDED 
                    : PaymentLedger.Status.PARTIALLY_REFUNDED;

                ledger.recordStep(step, nextStatus, 0.0, 0.0, amount, null);

                PaymentEvent event = new PaymentRefunded(paymentId, amount, now);

                return repository.save(paymentId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("payable", paymentId + ":refund"))
                    .flatMap(v -> telemetry.recordDuration("payable", paymentId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, PaymentLedger>right(ledger));
            })
            .yield((startTime, optRecord, result) -> result);
    }
}
