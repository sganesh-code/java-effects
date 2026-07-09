package io.effects.recipes.auditable;

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
 * An Object-Oriented "Recipe" representing an Auditable Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside AuditLedger).
 */
public final class AuditableProcess<ID, E, S> {
    private final StateRepository<ID, AuditLedger<ID, E>> repository;
    private final EventPublisher<AuditableEvent<ID>> publisher;
    private final TelemetryPort telemetry;
    private final ConcurrentMap<ID, AuditableRequest<ID, E, S>> assets = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public AuditableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public AuditableProcess(
        StateRepository<ID, AuditLedger<ID, E>> repository,
        EventPublisher<AuditableEvent<ID>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    /**
     * Registers a behavioral auditable request domain object.
     */
    public IO<Void> register(ID assetId, AuditableRequest<ID, E, S> request) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            assets.put(assetId, request);
            return null;
        });
    }

    /**
     * Creates/Initiates a new audit ledger.
     */
    public IO<Void> initiate(ID assetId) {
        Objects.requireNonNull(assetId);
        return repository.save(assetId, new AuditLedger<>(assetId));
    }

    /**
     * Records a new audit entry.
     */
    public IO<Either<String, AuditStep<E>>> record(ID assetId, String actorId, E detail, Instant now) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(detail);
        Objects.requireNonNull(now);

        AuditableRequest<ID, E, S> request = assets.get(assetId);
        if (request == null) {
            return IO.of(Either.left("Auditable asset not registered: " + assetId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(assetId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, AuditStep<E>>left("Audit ledger not found: " + assetId));
                }

                AuditLedger<ID, E> ledger = optLedger.get();
                Either<String, AuditStep<E>> tryRecordResult = ledger.recordEntry(actorId, detail, request, now);

                if (tryRecordResult.isLeft()) {
                    return IO.of(Either.<String, AuditStep<E>>left(tryRecordResult.getLeft()));
                }

                AuditStep<E> step = tryRecordResult.getRight();
                AuditableEvent<ID> event = new AuditableEvent.AuditRecorded<>(assetId, step, now);

                return repository.save(assetId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("auditable", assetId.toString() + ":record"))
                    .flatMap(v -> telemetry.recordDuration("auditable", assetId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, AuditStep<E>>right(step));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Replays history to reconstruct state.
     */
    public IO<Either<String, S>> replay(ID assetId) {
        Objects.requireNonNull(assetId);

        AuditableRequest<ID, E, S> request = assets.get(assetId);
        if (request == null) {
            return IO.of(Either.left("Auditable asset not registered: " + assetId));
        }

        return repository.find(assetId)
            .map(optLedger -> {
                if (optLedger.isEmpty()) {
                    return Either.<String, S>left("Audit ledger not found: " + assetId);
                }
                S state = request.reconstructState(optLedger.get().history());
                return Either.<String, S>right(state);
            });
    }

    /**
     * Explains a decision at a specific step in history.
     */
    public IO<Either<String, String>> explain(ID assetId, String decisionStepId) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(decisionStepId);

        AuditableRequest<ID, E, S> request = assets.get(assetId);
        if (request == null) {
            return IO.of(Either.left("Auditable asset not registered: " + assetId));
        }

        return repository.find(assetId)
            .map(optLedger -> {
                if (optLedger.isEmpty()) {
                    return Either.<String, String>left("Audit ledger not found: " + assetId);
                }
                String explanation = request.explainDecision(optLedger.get().history(), decisionStepId);
                return Either.<String, String>right(explanation);
            });
    }

    /**
     * Take a state snapshot and compact/clear ledger step memory.
     */
    public IO<Either<String, S>> snapshot(ID assetId, Instant now) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(now);

        AuditableRequest<ID, E, S> request = assets.get(assetId);
        if (request == null) {
            return IO.of(Either.left("Auditable asset not registered: " + assetId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(assetId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, S>left("Audit ledger not found: " + assetId));
                }

                AuditLedger<ID, E> ledger = optLedger.get();
                S state = request.reconstructState(ledger.history());
                AuditableEvent<ID> event = new AuditableEvent.SnapshotTaken<>(assetId, state, now);

                // Compact/clear in-memory steps to optimize memory footprints
                ledger.compact();

                return repository.save(assetId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("auditable", assetId.toString() + ":snapshot"))
                    .flatMap(v -> telemetry.recordDuration("auditable", assetId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, S>right(state));
            })
            .yield((startTime, optLedger, result) -> result);
    }
}