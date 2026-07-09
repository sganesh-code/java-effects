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
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An Object-Oriented "Recipe" representing a Payment Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside PaymentLedger).
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
                if (optRecord.isPresent() && optRecord.get().status() != PaymentLedger.Status.INITIAL) {
                    return IO.of(Either.<String, PaymentLedger>left("Cannot authorize: payment already initiated (current status: " + optRecord.get().status() + ")"));
                }

                // Delegate creation and transition to rich aggregate factory
                Either<String, TransitionResult<PaymentLedger, PaymentEvent>> authResult = PaymentLedger.authorize(
                    paymentId, actorId, amount, currency, payment, now
                );

                if (authResult.isLeft()) {
                    return IO.of(Either.<String, PaymentLedger>left(authResult.getLeft()));
                }

                TransitionResult<PaymentLedger, PaymentEvent> result = authResult.getRight();
                PaymentLedger ledger = result.aggregate();
                PaymentEvent event = result.event();

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

                // Delegate execution directly to rich aggregate!
                Either<String, PaymentEvent> eitherEvent = ledger.capture(actorId, amount, comment, payment, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, PaymentLedger>left(eitherEvent.getLeft()));
                }

                PaymentEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(paymentId, ledger)
                    .flatMap(v -> publishIO)
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

                // Delegate execution directly to rich aggregate!
                Either<String, PaymentEvent> eitherEvent = ledger.reverse(actorId, reason, payment, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, PaymentLedger>left(eitherEvent.getLeft()));
                }

                PaymentEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(paymentId, ledger)
                    .flatMap(v -> publishIO)
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

                // Delegate execution directly to rich aggregate!
                Either<String, PaymentEvent> eitherEvent = ledger.refund(actorId, amount, reason, payment, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, PaymentLedger>left(eitherEvent.getLeft()));
                }

                PaymentEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(paymentId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("payable", paymentId + ":refund"))
                    .flatMap(v -> telemetry.recordDuration("payable", paymentId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, PaymentLedger>right(ledger));
            })
            .yield((startTime, optRecord, result) -> result);
    }
}
