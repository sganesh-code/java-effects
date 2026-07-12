package io.effects.recipes.retryable;

import io.effects.recipes.retryable.models.*;
import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.StateRepository;
import io.effects.ports.TelemetryPort;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.Recipe;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An Object-Oriented "Recipe" representing a Retryable Process Manager.
 * It coordinates monadic persistence, failure categorization, backoff delays,
 * and publishes execution events.
 */
public final class RetryableProcess<ID, C> implements Recipe<ID, RetryableRequest<ID, C>> {
    private final StateRepository<ID, RetryLedger<ID, C>> repository;
    private final EventPublisher<RetryableEvent<ID>> publisher;
    private final TelemetryPort telemetry;
    private final ConcurrentMap<ID, RetryableRequest<ID, C>> requests = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public RetryableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public RetryableProcess(
        StateRepository<ID, RetryLedger<ID, C>> repository,
        EventPublisher<RetryableEvent<ID>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    @Override
    public IO<Void> register(ID operationId, RetryableRequest<ID, C> request) {
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            requests.put(operationId, request);
            return null;
        });
    }

    @Override
    public IO<Void> unregister(ID operationId) {
        Objects.requireNonNull(operationId);
        return IO.delay(() -> {
            requests.remove(operationId);
            return null;
        });
    }

    @Override
    public IO<Boolean> isRegistered(ID operationId) {
        Objects.requireNonNull(operationId);
        return IO.delay(() -> requests.containsKey(operationId));
    }

    /**
     * Executes a lazy monadic computation, automatically retrying on failure
     * according to the registered request's backoff and retry rules.
     */
    public <A> IO<Either<String, A>> execute(ID operationId, IO<A> action, C comment, Instant now) {
        Objects.requireNonNull(operationId);
        Objects.requireNonNull(action);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        RetryableRequest<ID, C> request = requests.get(operationId);
        if (request == null) {
            return IO.of(Either.left("Retryable request domain object not registered: " + operationId));
        }

        return repository.find(operationId)
            .flatMap(optLedger -> {
                RetryLedger<ID, C> ledger = optLedger.orElseGet(() -> RetryLedger.initiate(operationId));
                if (ledger.isTerminal()) {
                    return IO.of(Either.left("Cannot execute on terminal retry ledger: " + ledger.status()));
                }

                // 1. Record starting of the attempt
                Either<String, RetryableEvent<ID>> attemptEither = ledger.recordAttempt(now, comment);
                if (attemptEither.isLeft()) {
                    return IO.of(Either.left(attemptEither.getLeft()));
                }

                // Save starting state to repository
                return repository.save(operationId, ledger)
                    .flatMap(v -> telemetry.recordSuccess("retryable", operationId + ":attempt_start"))
                    .flatMap(v -> action.attempt()) // Run actual action computation
                    .flatMap(eitherResult -> {
                        Instant evaluationTime = now.plusMillis(100); // Simulate progress over time slightly
                        if (eitherResult.isRight()) {
                            // 2. Success path
                            A value = eitherResult.getRight();
                            Either<String, RetryableEvent<ID>> successEither = ledger.recordSuccess(evaluationTime, comment);
                            if (successEither.isLeft()) {
                                return IO.of(Either.left(successEither.getLeft()));
                            }

                            RetryableEvent<ID> successEvent = successEither.getRight();

                            return repository.save(operationId, ledger)
                                .flatMap(v -> publisher.publish(successEvent))
                                .flatMap(v -> telemetry.recordSuccess("retryable", operationId + ":success"))
                                .map(v -> Either.right(value));
                        } else {
                            // 3. Failure path
                            Throwable error = eitherResult.getLeft();
                            Either<String, RetryableEvent<ID>> failureEither = ledger.recordFailure(error, request, comment, evaluationTime);
                            if (failureEither.isLeft()) {
                                return IO.of(Either.left(failureEither.getLeft()));
                            }

                            RetryableEvent<ID> failureEvent = failureEither.getRight();

                            if (ledger.status() == RetryLedger.Status.RETRY_PENDING) {
                                // Scheduled for retry
                                long delay = request.calculateBackoffMillis(ledger.attempts(), evaluationTime);

                                return repository.save(operationId, ledger)
                                    .flatMap(v -> publisher.publish(failureEvent))
                                    .flatMap(v -> telemetry.recordSuccess("retryable", operationId + ":retry_scheduled"))
                                    .flatMap(v -> IO.delay(() -> {
                                        try {
                                            Thread.sleep(delay);
                                        } catch (InterruptedException ex) {
                                            Thread.currentThread().interrupt();
                                        }
                                        return null;
                                    }))
                                    // Recurse to retry the execution
                                    .flatMap(v -> execute(operationId, action, comment, evaluationTime.plusMillis(delay)));
                            } else {
                                // Fatal or max attempts exhausted -> Abandoned
                                return repository.save(operationId, ledger)
                                    .flatMap(v -> publisher.publish(failureEvent))
                                    .flatMap(v -> telemetry.recordSuccess("retryable", operationId + ":abandoned"))
                                    .map(v -> Either.left("Execution abandoned after " + ledger.attempts() + " attempts: " + error.getMessage()));
                            }
                        }
                    });
            });
    }
}
