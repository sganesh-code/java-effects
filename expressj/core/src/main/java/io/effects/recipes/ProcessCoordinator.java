package io.effects.recipes;

import io.effects.Either;
import io.effects.ForIO;
import io.effects.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.StateRepository;
import io.effects.ports.TelemetryPort;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A generic, thread-safe, and port-aware coordinator that orchestrates the transactional 
 * lifecycle of domain aggregate state ledgers. It handles fetching current states, applying 
 * pure business transitions, persisting updated states, publishing resulting domain events, 
 * and logging telemetry success metrics and latencies within a functional IO context.
 *
 * @param <K> the type of unique keys/identifiers for the ledger state
 * @param <L> the type of rich domain aggregate state ledgers
 * @param <E> the type of domain events emitted by the ledger
 */
public final class ProcessCoordinator<K, L, E> {
    private final StateRepository<K, L> repository;
    private final EventPublisher<E> publisher;
    private final TelemetryPort telemetry;
    private final String telemetryCategory;

    /**
     * Constructs a ProcessCoordinator with specified ports and category logging names.
     */
    public ProcessCoordinator(
        StateRepository<K, L> repository,
        EventPublisher<E> publisher,
        TelemetryPort telemetry,
        String telemetryCategory
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.telemetryCategory = Objects.requireNonNull(telemetryCategory);
    }

    /**
     * Executes a transactional state transition on a domain ledger, manages its lifecycle persistence, 
     * publishes the resulting domain event, and records operations telemetry and execution timing.
     *
     * @param <R> the return type of the mapped result
     * @param key the identifier key of the aggregate ledger
     * @param operationName the name of the operation for telemetry logging
     * @param transition a function representing the pure domain transition to apply
     * @param resultMapper a function to map the finalized ledger state to the returned result type
     * @return an IO computation yielding either a string error description or the mapped transition result
     */
    public <R> IO<Either<String, R>> coordinate(
        K key,
        String operationName,
        Function<Optional<L>, Either<String, TransitionResult<L, E>>> transition,
        Function<L, R> resultMapper
    ) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(operationName);
        Objects.requireNonNull(transition);
        Objects.requireNonNull(resultMapper);

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(key))
            .bind((startTime, optLedger) -> {
                // 1. Run the pure business state transition
                Either<String, TransitionResult<L, E>> transResult = transition.apply(optLedger);
                if (transResult.isLeft()) {
                    return IO.of(Either.<String, R>left(transResult.getLeft()));
                }

                TransitionResult<L, E> result = transResult.getRight();
                L updatedLedger = result.aggregate();
                E event = result.event();

                // 2. Save state, publish event, and log metrics inside monadic IO context
                return repository.save(key, updatedLedger)
                    .flatMap(v -> event != null ? publisher.publish(event) : IO.of(null))
                    .flatMap(v -> telemetry.recordSuccess(telemetryCategory, key.toString() + ":" + operationName))
                    .flatMap(v -> telemetry.recordDuration(telemetryCategory, key.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, R>right(resultMapper.apply(updatedLedger)));
            })
            .yield((startTime, optLedger, result) -> result);
    }
}
