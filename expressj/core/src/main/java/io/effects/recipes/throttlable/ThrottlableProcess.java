package io.effects.recipes.throttlable;

import io.effects.recipes.throttlable.models.*;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * An Object-Oriented "Recipe" representing a Throttlable Process Manager (Rate Limiter).
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside TokenBucketLedger).
 */
public final class ThrottlableProcess<ID, C> implements Recipe<ID, ThrottlableRequest<ID, C>> {
    private final StateRepository<ID, TokenBucketLedger<ID, C>> repository;
    private final EventPublisher<ThrottlableEvent<ID>> publisher;
    private final TelemetryPort telemetry;
    private final ProcessCoordinator<ID, TokenBucketLedger<ID, C>, ThrottlableEvent<ID>> coordinator;
    private final ConcurrentMap<ID, ThrottlableRequest<ID, C>> requests = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public ThrottlableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public ThrottlableProcess(
        StateRepository<ID, TokenBucketLedger<ID, C>> repository,
        EventPublisher<ThrottlableEvent<ID>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.coordinator = new ProcessCoordinator<>(repository, publisher, telemetry, "throttlable");
    }

    @Override
    public IO<Void> register(ID actorId, ThrottlableRequest<ID, C> request) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            requests.put(actorId, request);
            return null;
        });
    }

    @Override
    public IO<Void> unregister(ID actorId) {
        Objects.requireNonNull(actorId);
        return IO.delay(() -> {
            requests.remove(actorId);
            return null;
        });
    }

    @Override
    public IO<Boolean> isRegistered(ID actorId) {
        Objects.requireNonNull(actorId);
        return IO.delay(() -> requests.containsKey(actorId));
    }

    /**
     * Coordinates token consumption from the bucket, applying adaptive refills first.
     */
    public IO<Either<String, TokenBucketLedger<ID, C>>> consume(ID actorId, double requestedTokens, C comment, Instant now) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        ThrottlableRequest<ID, C> request = requests.get(actorId);
        if (request == null) {
            return IO.of(Either.left("Throttlable request domain object not registered: " + actorId));
        }

        return coordinator.coordinate(
            actorId,
            "consume",
            optRecord -> {
                TokenBucketLedger<ID, C> ledger = optRecord.orElseGet(() -> TokenBucketLedger.initiate(actorId));
                Either<String, ThrottlableEvent<ID>> eitherEvent = ledger.consume(requestedTokens, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }
}
