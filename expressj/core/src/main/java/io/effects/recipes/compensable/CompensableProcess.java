package io.effects.recipes.compensable;

import io.effects.recipes.compensable.models.*;
import io.effects.core.Either;
import io.effects.core.IO;
import io.effects.ports.EventPublisher;
import io.effects.ports.StateRepository;
import io.effects.ports.TelemetryPort;
import io.effects.adapters.InMemoryEventPublisher;
import io.effects.adapters.InMemoryStateRepository;
import io.effects.adapters.NoOpTelemetryPort;
import io.effects.recipes.ProcessCoordinator;
import io.effects.recipes.Recipe;
import io.effects.recipes.TransitionResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * An Object-Oriented "Recipe" representing a Compensable Process Manager (Saga Coordinator).
 * It orchestrates step executions, registers their compensating actions, and triggers reverse
 * rollbacks if any step fails.
 */
public final class CompensableProcess<ID, C> implements Recipe<ID, CompensableRequest<ID, C>> {
    private final StateRepository<ID, CompensationLedger<ID, C>> repository;
    private final EventPublisher<CompensableEvent<ID>> publisher;
    private final TelemetryPort telemetry;
    private final ProcessCoordinator<ID, CompensationLedger<ID, C>, CompensableEvent<ID>> coordinator;
    private final ConcurrentMap<ID, CompensableRequest<ID, C>> requests = new ConcurrentHashMap<>();
    private final ConcurrentMap<ID, ConcurrentMap<String, IO<Void>>> compensationRegistry = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public CompensableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public CompensableProcess(
        StateRepository<ID, CompensationLedger<ID, C>> repository,
        EventPublisher<CompensableEvent<ID>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.coordinator = new ProcessCoordinator<>(repository, publisher, telemetry, "compensable");
    }

    @Override
    public IO<Void> register(ID transactionId, CompensableRequest<ID, C> request) {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            requests.put(transactionId, request);
            compensationRegistry.put(transactionId, new ConcurrentHashMap<>());
            return null;
        });
    }

    @Override
    public IO<Void> unregister(ID transactionId) {
        Objects.requireNonNull(transactionId);
        return IO.delay(() -> {
            requests.remove(transactionId);
            compensationRegistry.remove(transactionId);
            return null;
        });
    }

    @Override
    public IO<Boolean> isRegistered(ID transactionId) {
        Objects.requireNonNull(transactionId);
        return IO.delay(() -> requests.containsKey(transactionId));
    }

    /**
     * Executes a single step of the Saga.
     * If the step's action succeeds, it is recorded.
     * If it fails, the process triggers an automated compensation rollback of all completed steps.
     */
    public <A> IO<Either<String, A>> runStep(
        ID transactionId, 
        String stepId, 
        IO<A> action, 
        IO<Void> compensation, 
        C comment, 
        Instant now
    ) {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(action);
        Objects.requireNonNull(compensation);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        CompensableRequest<ID, C> request = requests.get(transactionId);
        if (request == null) {
            return IO.of(Either.left("Compensable request domain object not registered: " + transactionId));
        }

        // Register the compensation action locally
        ConcurrentMap<String, IO<Void>> transactionCompensations = compensationRegistry.get(transactionId);
        if (transactionCompensations != null) {
            transactionCompensations.put(stepId, compensation);
        }

        // Run the actual step computation
        return action.attempt().flatMap(eitherResult -> {
            if (eitherResult.isRight()) {
                // Success: record the step success in the ledger
                A value = eitherResult.getRight();
                return coordinator.coordinate(
                    transactionId,
                    "markStepSuccess",
                    optRecord -> {
                        CompensationLedger<ID, C> ledger = optRecord.orElseGet(() -> CompensationLedger.initiate(transactionId));
                        Either<String, CompensableEvent<ID>> eitherEvent = ledger.markStepSuccess(stepId, comment, request, now);
                        return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
                    },
                    Function.identity()
                ).map(eitherLedger -> eitherLedger.map(ledger -> value));
            } else {
                // Failure: trigger the compensation rollback sequence
                Throwable error = eitherResult.getLeft();
                return triggerRollbackSequence(transactionId, stepId, error, comment, now)
                    .map(eitherRollback -> Either.left("Step " + stepId + " failed. Rollback initiated: " + error.getMessage()));
            }
        });
    }

    /**
     * Completes the entire Saga successfully, transitioning the state ledger to COMPLETED.
     */
    public IO<Either<String, CompensationLedger<ID, C>>> completeSaga(ID transactionId, C comment, Instant now) {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        CompensableRequest<ID, C> request = requests.get(transactionId);
        if (request == null) {
            return IO.of(Either.left("Compensable request domain object not registered: " + transactionId));
        }

        return coordinator.coordinate(
            transactionId,
            "completeSaga",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Compensation ledger not found for transaction: " + transactionId);
                }
                CompensationLedger<ID, C> ledger = optRecord.get();
                Either<String, CompensableEvent<ID>> eitherEvent = ledger.markCompleted(comment, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Internal: Coordinates triggering the Saga rollback and executing all completed compensations in reverse order.
     */
    private IO<Either<String, CompensationLedger<ID, C>>> triggerRollbackSequence(
        ID transactionId, 
        String failedStepId, 
        Throwable error, 
        C comment, 
        Instant now
    ) {
        CompensableRequest<ID, C> request = requests.get(transactionId);

        // 1. Trigger rollback state change in aggregate
        return coordinator.coordinate(
            transactionId,
            "triggerRollback",
            optRecord -> {
                CompensationLedger<ID, C> ledger = optRecord.orElseGet(() -> CompensationLedger.initiate(transactionId));
                Either<String, CompensableEvent<ID>> eitherEvent = ledger.triggerRollback(failedStepId, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        ).flatMap(eitherLedger -> {
            if (eitherLedger.isLeft()) {
                return IO.of(Either.left(eitherLedger.getLeft()));
            }

            CompensationLedger<ID, C> ledger = eitherLedger.getRight();

            // 2. Resolve completed steps to compensate
            class ProjectorResult {
                List<String> completed = Collections.emptyList();
            }
            ProjectorResult pr = new ProjectorResult();
            ledger.projectState((tid, status, completedSteps, history) -> pr.completed = completedSteps);

            // Create a copy and reverse to execute compensation LIFO (Last-In-First-Out)
            List<String> stepsToCompensate = new ArrayList<>(pr.completed);
            Collections.reverse(stepsToCompensate);

            // Execute all compensating actions sequentially
            IO<Void> compensationChain = IO.of(null);
            ConcurrentMap<String, IO<Void>> transactionCompensations = compensationRegistry.get(transactionId);

            for (String stepId : stepsToCompensate) {
                IO<Void> compAction = transactionCompensations != null ? transactionCompensations.get(stepId) : null;
                if (compAction != null) {
                    compensationChain = compensationChain.flatMap(v -> compAction);
                }
            }

            // 3. Execute chain and update ledger with final status (COMPENSATED or FAILED)
            return compensationChain.attempt().flatMap(eitherCompResult -> {
                if (eitherCompResult.isRight()) {
                    // Success: mark ledger as compensated
                    return coordinator.coordinate(
                        transactionId,
                        "markCompensated",
                        optRecord -> {
                            CompensationLedger<ID, C> l = optRecord.orElseThrow();
                            Either<String, CompensableEvent<ID>> eitherEvent = l.markCompensated(comment, now.plusMillis(100));
                            return eitherEvent.map(event -> new TransitionResult<>(l, event));
                        },
                        Function.identity()
                    );
                } else {
                    // Failure: record compensation failure
                    Throwable compErr = eitherCompResult.getLeft();
                    return coordinator.coordinate(
                        transactionId,
                        "markCompensationFailure",
                        optRecord -> {
                            CompensationLedger<ID, C> l = optRecord.orElseThrow();
                            Either<String, CompensableEvent<ID>> eitherEvent = l.markCompensationFailure(
                                failedStepId, 
                                compErr.getMessage(), 
                                comment, 
                                now.plusMillis(100)
                            );
                            return eitherEvent.map(event -> new TransitionResult<>(l, event));
                        },
                        Function.identity()
                    );
                }
            });
        });
    }
}
