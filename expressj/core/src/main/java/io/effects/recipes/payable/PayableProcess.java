package io.effects.recipes.payable;

import io.effects.Either;
import io.effects.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.StateRepository;
import io.effects.ports.TelemetryPort;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.ProcessCoordinator;
import io.effects.recipes.ProcessRegistry;
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * An Object-Oriented "Recipe" representing a Payment Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside PaymentLedger).
 */
public final class PayableProcess<ID, M> implements ProcessRegistry<ID, PayableRequest<ID, M>> {
    private final StateRepository<ID, PaymentLedger<ID, M>> repository;
    private final EventPublisher<PaymentEvent<ID, M>> publisher;
    private final TelemetryPort telemetry;
    private final ProcessCoordinator<ID, PaymentLedger<ID, M>, PaymentEvent<ID, M>> coordinator;
    private final ConcurrentMap<ID, PayableRequest<ID, M>> payments = new ConcurrentHashMap<>();

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
        StateRepository<ID, PaymentLedger<ID, M>> repository,
        EventPublisher<PaymentEvent<ID, M>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.coordinator = new ProcessCoordinator<>(repository, publisher, telemetry, "payable");
    }

    /**
     * Registers a behavioral payment request domain object.
     */
    @Override
    public IO<Void> register(ID paymentId, PayableRequest<ID, M> payment) {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(payment);
        return IO.delay(() -> {
            payments.put(paymentId, payment);
            return null;
        });
    }

    @Override
    public IO<Void> unregister(ID paymentId) {
        Objects.requireNonNull(paymentId);
        return IO.delay(() -> {
            payments.remove(paymentId);
            return null;
        });
    }

    @Override
    public IO<Boolean> isRegistered(ID paymentId) {
        Objects.requireNonNull(paymentId);
        return IO.delay(() -> payments.containsKey(paymentId));
    }

    /**
     * Authorizes an initial payment amount.
     */
    public IO<Either<String, PaymentLedger<ID, M>>> authorize(ID paymentId, String actorId, M detail, Instant now) {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(now);

        PayableRequest<ID, M> payment = payments.get(paymentId);
        if (payment == null) {
            return IO.of(Either.left("Payment domain object not registered: " + paymentId));
        }

        return coordinator.coordinate(
            paymentId,
            "authorize",
            optRecord -> {
                if (optRecord.isPresent() && optRecord.get().status() != PaymentLedger.Status.INITIAL) {
                    return Either.left("Cannot authorize: payment already initiated (current status: " + optRecord.get().status() + ")");
                }
                return PaymentLedger.authorize(paymentId, actorId, detail, payment, now);
            },
            Function.identity()
        );
    }

    /**
     * Captures an authorized payment.
     */
    public IO<Either<String, PaymentLedger<ID, M>>> capture(ID paymentId, String actorId, M detail, String comment, Instant now) {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        PayableRequest<ID, M> payment = payments.get(paymentId);
        if (payment == null) {
            return IO.of(Either.left("Payment domain object not registered: " + paymentId));
        }

        return coordinator.coordinate(
            paymentId,
            "capture",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Payment ledger not found: " + paymentId);
                }
                PaymentLedger<ID, M> ledger = optRecord.get();
                Either<String, PaymentEvent<ID, M>> eitherEvent = ledger.capture(actorId, detail, comment, payment, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Reverses/voids an active payment authorization.
     */
    public IO<Either<String, PaymentLedger<ID, M>>> reverse(ID paymentId, String actorId, String reason, Instant now) {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);

        PayableRequest<ID, M> payment = payments.get(paymentId);
        if (payment == null) {
            return IO.of(Either.left("Payment domain object not registered: " + paymentId));
        }

        return coordinator.coordinate(
            paymentId,
            "reverse",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Payment ledger not found: " + paymentId);
                }
                PaymentLedger<ID, M> ledger = optRecord.get();
                Either<String, PaymentEvent<ID, M>> eitherEvent = ledger.reverse(actorId, reason, payment, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Refunds a captured payment.
     */
    public IO<Either<String, PaymentLedger<ID, M>>> refund(ID paymentId, String actorId, M detail, String reason, Instant now) {
        Objects.requireNonNull(paymentId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);

        PayableRequest<ID, M> payment = payments.get(paymentId);
        if (payment == null) {
            return IO.of(Either.left("Payment domain object not registered: " + paymentId));
        }

        return coordinator.coordinate(
            paymentId,
            "refund",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Payment ledger not found: " + paymentId);
                }
                PaymentLedger<ID, M> ledger = optRecord.get();
                Either<String, PaymentEvent<ID, M>> eitherEvent = ledger.refund(actorId, detail, reason, payment, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }
}
