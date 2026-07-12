package io.effects.recipes.meterable;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An Object-Oriented "Recipe" representing a Meterable Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside MeterLedger).
 */
public final class MeterableProcess<ID, U, R> {
    private final StateRepository<ID, MeterLedger<ID, U>> repository;
    private final EventPublisher<MeterableEvent<ID>> publisher;
    private final TelemetryPort telemetry;
    private final ConcurrentMap<ID, MeterableRequest<ID, U, R>> meters = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public MeterableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public MeterableProcess(
        StateRepository<ID, MeterLedger<ID, U>> repository,
        EventPublisher<MeterableEvent<ID>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    /**
     * Registers a behavioral meterable request domain object.
     */
    public IO<Void> register(ID accountId, MeterableRequest<ID, U, R> request) {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            meters.put(accountId, request);
            return null;
        });
    }

    /**
     * Creates/Initiates a new usage meter ledger for an account.
     */
    public IO<Void> initiate(ID accountId) {
        Objects.requireNonNull(accountId);
        return repository.save(accountId, new MeterLedger<>(accountId));
    }

    /**
     * Starts the usage billing meter cycle.
     */
    public IO<Either<String, MeterLedger<ID, U>>> start(ID accountId, Instant now) {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(now);

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(accountId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, MeterLedger<ID, U>>left("Meter ledger not found: " + accountId));
                }

                MeterLedger<ID, U> ledger = optLedger.get();
                Either<String, Void> tryStart = ledger.start(now);

                if (tryStart.isLeft()) {
                    return IO.of(Either.<String, MeterLedger<ID, U>>left(tryStart.getLeft()));
                }

                MeterableEvent<ID> event = new MeterableEvent.MeterStarted<>(accountId, now);

                return repository.save(accountId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("meterable", accountId.toString() + ":start"))
                    .flatMap(v -> telemetry.recordDuration("meterable", accountId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, MeterLedger<ID, U>>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Records a new discrete usage tick.
     */
    public IO<Either<String, UsageStep<U>>> recordUsage(ID accountId, U metric, Instant now) {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(metric);
        Objects.requireNonNull(now);

        MeterableRequest<ID, U, R> request = meters.get(accountId);
        if (request == null) {
            return IO.of(Either.left("Meterable request domain object not registered: " + accountId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(accountId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, UsageStep<U>>left("Meter ledger not found: " + accountId));
                }

                MeterLedger<ID, U> ledger = optLedger.get();
                Either<String, UsageStep<U>> tryRecordResult = ledger.recordUsage(metric, request, now);

                if (tryRecordResult.isLeft()) {
                    return IO.of(Either.<String, UsageStep<U>>left(tryRecordResult.getLeft()));
                }

                UsageStep<U> step = tryRecordResult.getRight();
                MeterableEvent<ID> event = new MeterableEvent.UsageRecorded<>(accountId, step, now);

                return repository.save(accountId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("meterable", accountId.toString() + ":register"))
                    .flatMap(v -> telemetry.recordDuration("meterable", accountId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, UsageStep<U>>right(step));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Rates the recorded usage history, finalizes the billing cycle, and resets state.
     */
    public IO<Either<String, R>> rate(ID accountId, Instant now) {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(now);

        MeterableRequest<ID, U, R> request = meters.get(accountId);
        if (request == null) {
            return IO.of(Either.left("Meterable request domain object not registered: " + accountId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(accountId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, R>left("Meter ledger not found: " + accountId));
                }

                MeterLedger<ID, U> ledger = optLedger.get();
                Either<String, R> tryRateResult = ledger.rate(request, now);

                if (tryRateResult.isLeft()) {
                    return IO.of(Either.<String, R>left(tryRateResult.getLeft()));
                }

                R rating = tryRateResult.getRight();
                MeterableEvent<ID> event = new MeterableEvent.MeterRated<>(accountId, rating, now);

                // Compact to optimize memory footprints after billing finalized
                ledger.compact();

                return repository.save(accountId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("meterable", accountId.toString() + ":rate"))
                    .flatMap(v -> telemetry.recordDuration("meterable", accountId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, R>right(rating));
            })
            .yield((startTime, optLedger, result) -> result);
    }
}