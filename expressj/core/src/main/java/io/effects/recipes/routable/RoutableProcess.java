package io.effects.recipes.routable;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * An Object-Oriented "Recipe" representing a Routable Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside RouteLedger).
 */
public final class RoutableProcess<ID, H, C> implements Recipe<ID, RoutableRequest<ID, H, C>> {
    private final ProcessCoordinator<ID, RouteLedger<ID, H, C>, RoutableEvent<ID, H>> coordinator;
    private final ConcurrentMap<ID, RoutableRequest<ID, H, C>> requests = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public RoutableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public RoutableProcess(
        StateRepository<ID, RouteLedger<ID, H, C>> repository,
        EventPublisher<RoutableEvent<ID, H>> publisher,
        TelemetryPort telemetry
    ) {
        this.coordinator = new ProcessCoordinator<>(repository, publisher, telemetry, "routable");
    }

    @Override
    public IO<Void> register(ID workId, RoutableRequest<ID, H, C> request) {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            requests.put(workId, request);
            return null;
        });
    }

    @Override
    public IO<Void> unregister(ID workId) {
        Objects.requireNonNull(workId);
        return IO.delay(() -> {
            requests.remove(workId);
            return null;
        });
    }

    @Override
    public IO<Boolean> isRegistered(ID workId) {
        Objects.requireNonNull(workId);
        return IO.delay(() -> requests.containsKey(workId));
    }

    /**
     * Coordinates the routing of work to an initial proposed handler.
     */
    public IO<Either<String, RouteLedger<ID, H, C>>> route(ID workId, H proposedHandler, C comment, Instant now) {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(proposedHandler);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        RoutableRequest<ID, H, C> request = requests.get(workId);
        if (request == null) {
            return IO.of(Either.left("Routable request domain object not registered: " + workId));
        }

        return coordinator.coordinate(
            workId,
            "route",
            optRecord -> {
                if (optRecord.isPresent() && optRecord.get().status() != RouteLedger.Status.UNROUTED) {
                    return Either.left("Work has already been routed: " + workId);
                }
                RouteLedger<ID, H, C> ledger = optRecord.orElseGet(() -> RouteLedger.initiate(workId));
                Either<String, RoutableEvent<ID, H>> eitherEvent = ledger.route(proposedHandler, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates the rerouting of work from current handler to a new handler.
     */
    public IO<Either<String, RouteLedger<ID, H, C>>> reroute(ID workId, H proposedHandler, C comment, Instant now) {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(proposedHandler);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        RoutableRequest<ID, H, C> request = requests.get(workId);
        if (request == null) {
            return IO.of(Either.left("Routable request domain object not registered: " + workId));
        }

        return coordinator.coordinate(
            workId,
            "reroute",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Route ledger not found for work: " + workId);
                }
                RouteLedger<ID, H, C> ledger = optRecord.get();
                Either<String, RoutableEvent<ID, H>> eitherEvent = ledger.reroute(proposedHandler, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates the rejection of a routing flow.
     */
    public IO<Either<String, RouteLedger<ID, H, C>>> reject(ID workId, C reason, Instant now) {
        Objects.requireNonNull(workId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);

        RoutableRequest<ID, H, C> request = requests.get(workId);
        if (request == null) {
            return IO.of(Either.left("Routable request domain object not registered: " + workId));
        }

        return coordinator.coordinate(
            workId,
            "reject",
            optRecord -> {
                RouteLedger<ID, H, C> ledger = optRecord.orElseGet(() -> RouteLedger.initiate(workId));
                Either<String, RoutableEvent<ID, H>> eitherEvent = ledger.reject(reason, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }
}
