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
public final class SchedulableProcess {
    private final StateRepository<String, ScheduleLedger> repository;
    private final EventPublisher<SchedulableEvent> publisher;
    private final TelemetryPort telemetry;
    private final ConcurrentMap<String, SchedulableRequest> schedules = new ConcurrentHashMap<>();

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
        StateRepository<String, ScheduleLedger> repository,
        EventPublisher<SchedulableEvent> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    /**
     * Registers a behavioral schedulable request domain object.
     */
    public IO<Void> register(String occurrenceId, SchedulableRequest request) {
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
    public IO<Either<String, ScheduleLedger>> schedule(String occurrenceId, String actorId, Instant triggerTime, Instant now) {
        Objects.requireNonNull(occurrenceId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(triggerTime);
        Objects.requireNonNull(now);

        SchedulableRequest request = schedules.get(occurrenceId);
        if (request == null) {
            return IO.of(Either.left("Schedulable domain object not registered: " + occurrenceId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(occurrenceId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isPresent() && optLedger.get().status() != ScheduleLedger.Status.INITIAL) {
                    return IO.of(Either.<String, ScheduleLedger>left("Cannot schedule: occurrence already scheduled (current status: " + optLedger.get().status() + ")"));
                }

                // Delegate creation and transition to rich aggregate factory
                Either<String, TransitionResult<ScheduleLedger, SchedulableEvent>> schedResult = ScheduleLedger.schedule(
                    occurrenceId, actorId, triggerTime, request, now
                );

                if (schedResult.isLeft()) {
                    return IO.of(Either.<String, ScheduleLedger>left(schedResult.getLeft()));
                }

                TransitionResult<ScheduleLedger, SchedulableEvent> result = schedResult.getRight();
                ScheduleLedger ledger = result.aggregate();
                SchedulableEvent event = result.event();

                return repository.save(occurrenceId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("schedulable", occurrenceId + ":schedule"))
                    .flatMap(v -> telemetry.recordDuration("schedulable", occurrenceId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, ScheduleLedger>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Reschedules/adjusts an active scheduled trigger time.
     */
    public IO<Either<String, ScheduleLedger>> reschedule(String occurrenceId, String actorId, Instant newTriggerTime, String comment, Instant now) {
        Objects.requireNonNull(occurrenceId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(newTriggerTime);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        SchedulableRequest request = schedules.get(occurrenceId);
        if (request == null) {
            return IO.of(Either.left("Schedulable domain object not registered: " + occurrenceId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(occurrenceId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, ScheduleLedger>left("Schedule ledger not found: " + occurrenceId));
                }

                ScheduleLedger ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, SchedulableEvent> eitherEvent = ledger.reschedule(actorId, newTriggerTime, comment, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, ScheduleLedger>left(eitherEvent.getLeft()));
                }

                SchedulableEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(occurrenceId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("schedulable", occurrenceId + ":reschedule"))
                    .flatMap(v -> telemetry.recordDuration("schedulable", occurrenceId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, ScheduleLedger>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Fires/executes the scheduled occurrence.
     */
    public IO<Either<String, ScheduleLedger>> fire(String occurrenceId, String actorId, Instant now) {
        Objects.requireNonNull(occurrenceId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(now);

        SchedulableRequest request = schedules.get(occurrenceId);
        if (request == null) {
            return IO.of(Either.left("Schedulable domain object not registered: " + occurrenceId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(occurrenceId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, ScheduleLedger>left("Schedule ledger not found: " + occurrenceId));
                }

                ScheduleLedger ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, SchedulableEvent> eitherEvent = ledger.fire(actorId, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, ScheduleLedger>left(eitherEvent.getLeft()));
                }

                SchedulableEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(occurrenceId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("schedulable", occurrenceId + ":fire"))
                    .flatMap(v -> telemetry.recordDuration("schedulable", occurrenceId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, ScheduleLedger>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Cancels the scheduled occurrence.
     */
    public IO<Either<String, ScheduleLedger>> cancel(String occurrenceId, String actorId, String reason, Instant now) {
        Objects.requireNonNull(occurrenceId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);

        SchedulableRequest request = schedules.get(occurrenceId);
        if (request == null) {
            return IO.of(Either.left("Schedulable domain object not registered: " + occurrenceId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(occurrenceId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, ScheduleLedger>left("Schedule ledger not found: " + occurrenceId));
                }

                ScheduleLedger ledger = optLedger.get();

                // Delegate execution directly to rich aggregate!
                Either<String, SchedulableEvent> eitherEvent = ledger.cancel(actorId, reason, request, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, ScheduleLedger>left(eitherEvent.getLeft()));
                }

                SchedulableEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(occurrenceId, ledger)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("schedulable", occurrenceId + ":cancel"))
                    .flatMap(v -> telemetry.recordDuration("schedulable", occurrenceId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, ScheduleLedger>right(ledger));
            })
            .yield((startTime, optLedger, result) -> result);
    }
}
