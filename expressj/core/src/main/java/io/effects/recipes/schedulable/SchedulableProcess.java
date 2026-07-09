package io.effects.recipes.schedulable;

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
 * An Object-Oriented "Recipe" representing a Schedulable Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside ScheduleLedger).
 */
public final class SchedulableProcess<ID, T> {
    private final StateRepository<ID, ScheduleLedger<ID, T>> repository;
    private final EventPublisher<SchedulableEvent<ID, T>> publisher;
    private final TelemetryPort telemetry;
    private final ConcurrentMap<ID, SchedulableRequest<ID, T>> schedules = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public SchedulableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public SchedulableProcess(
        StateRepository<ID, ScheduleLedger<ID, T>> repository,
        EventPublisher<SchedulableEvent<ID, T>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    /**
     * Registers a behavioral schedulable request domain object.
     */
    public IO<Void> register(ID occurrenceId, SchedulableRequest<ID, T> request) {
        Objects.requireNonNull(occurrenceId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            schedules.put(occurrenceId, request);
            return null;
        });
    }

    /**
     * Schedules an occurrence initially.
     */
    public IO<Either<String, ScheduleLedger<ID, T>>> schedule(ID occurrenceId, String actorId, T triggerTime, Instant now) {
        Objects.requireNonNull(occurrenceId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(triggerTime);
        Objects.requireNonNull(now);

        SchedulableRequest<ID, T> request = schedules.get(occurrenceId);
        if (request == null) {
            return IO.of(Either.left("Schedulable domain object not registered: " + occurrenceId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(occurrenceId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isPresent() && optLedger.get().status() != ScheduleLedger.Status.INITIAL) {
                    return IO.of(Either.<String, ScheduleLedger<ID, T>>left("Cannot schedule: occurrence already scheduled (current status: " + optLedger.get().status() + ")"));
                }

                // Delegate creation and transition to rich aggregate factory
                Either<String, TransitionResult<ScheduleLedger<ID, T>, SchedulableEvent<ID, T>>> schedResult = ScheduleLedger.schedule(
                    occurrenceId, actorId, triggerTime, request, now
                );

                if (schedResult.isLeft()) {
                    return IO.of(Either.<String, ScheduleLedger<ID, T>>left(schedResult.getLeft()));
                }

                TransitionResult<ScheduleLedger<ID, T>, SchedulableEvent<ID, T>> result = schedResult.getRight();
                ScheduleLedger<ID, T> ledger = result.aggregate();
                SchedulableEvent<ID, T> event = result.event();

                return repository.save(occurrenceId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("schedulable", occurrenceId.toString() + ":schedule"))
                    .flatMap(v -> telemetry.recordDuration("schedulable", occurrenceId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, ScheduleLedger<ID, T>>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Reschedules/adjusts an active scheduled trigger time.
     */
    public IO<Either<String, ScheduleLedger<ID, T>>> reschedule(ID occurrenceId, String actorId, T newTriggerTime, String comment, Instant now) {
        Objects.requireNonNull(occurrenceId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(newTriggerTime);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        SchedulableRequest<ID, T> request = schedules.get(occurrenceId);
        if (request == null) {
            return IO.of(Either.left("Schedulable domain object not registered: " + occurrenceId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(occurrenceId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, ScheduleLedger<ID, T>>left("Schedule ledger not found: " + occurrenceId));
                }

                ScheduleLedger<ID, T> ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, SchedulableEvent<ID, T>> eitherEvent = ledger.reschedule(actorId, newTriggerTime, comment, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, ScheduleLedger<ID, T>>left(eitherEvent.getLeft()));
                }

                SchedulableEvent<ID, T> event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(occurrenceId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("schedulable", occurrenceId.toString() + ":reschedule"))
                    .flatMap(v -> telemetry.recordDuration("schedulable", occurrenceId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, ScheduleLedger<ID, T>>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Fires/executes the scheduled occurrence.
     */
    public IO<Either<String, ScheduleLedger<ID, T>>> fire(ID occurrenceId, String actorId, Instant now) {
        Objects.requireNonNull(occurrenceId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(now);

        SchedulableRequest<ID, T> request = schedules.get(occurrenceId);
        if (request == null) {
            return IO.of(Either.left("Schedulable domain object not registered: " + occurrenceId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(occurrenceId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, ScheduleLedger<ID, T>>left("Schedule ledger not found: " + occurrenceId));
                }

                ScheduleLedger<ID, T> ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, SchedulableEvent<ID, T>> eitherEvent = ledger.fire(actorId, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, ScheduleLedger<ID, T>>left(eitherEvent.getLeft()));
                }

                SchedulableEvent<ID, T> event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(occurrenceId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("schedulable", occurrenceId.toString() + ":fire"))
                    .flatMap(v -> telemetry.recordDuration("schedulable", occurrenceId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, ScheduleLedger<ID, T>>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Cancels the scheduled occurrence.
     */
    public IO<Either<String, ScheduleLedger<ID, T>>> cancel(ID occurrenceId, String actorId, String reason, Instant now) {
        Objects.requireNonNull(occurrenceId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);

        SchedulableRequest<ID, T> request = schedules.get(occurrenceId);
        if (request == null) {
            return IO.of(Either.left("Schedulable domain object not registered: " + occurrenceId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(occurrenceId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, ScheduleLedger<ID, T>>left("Schedule ledger not found: " + occurrenceId));
                }

                ScheduleLedger<ID, T> ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, SchedulableEvent<ID, T>> eitherEvent = ledger.cancel(actorId, reason, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, ScheduleLedger<ID, T>>left(eitherEvent.getLeft()));
                }

                SchedulableEvent<ID, T> event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(occurrenceId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("schedulable", occurrenceId.toString() + ":cancel"))
                    .flatMap(v -> telemetry.recordDuration("schedulable", occurrenceId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, ScheduleLedger<ID, T>>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }
}