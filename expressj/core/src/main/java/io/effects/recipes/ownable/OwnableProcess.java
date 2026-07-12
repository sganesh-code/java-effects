package io.effects.recipes.ownable;

import io.effects.Either;
import io.effects.IO;
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
 * An Object-Oriented "Recipe" representing an Ownership Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside OwnershipRecord).
 */
public final class OwnableProcess<ID, O> implements Recipe<ID, OwnableRequest<ID, O>> {
    private final StateRepository<ID, OwnershipRecord<ID, O>> repository;
    private final EventPublisher<OwnershipEvent<ID, O>> publisher;
    private final TelemetryPort telemetry;
    private final ProcessCoordinator<ID, OwnershipRecord<ID, O>, OwnershipEvent<ID, O>> coordinator;
    private final ConcurrentMap<ID, OwnableRequest<ID, O>> assets = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public OwnableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public OwnableProcess(
        StateRepository<ID, OwnershipRecord<ID, O>> repository,
        EventPublisher<OwnershipEvent<ID, O>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.coordinator = new ProcessCoordinator<>(repository, publisher, telemetry, "ownable");
    }

    /**
     * Registers a behavioral ownable request domain object.
     */
    @Override
    public IO<Void> register(ID assetId, OwnableRequest<ID, O> asset) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(asset);
        return IO.delay(() -> {
            assets.put(assetId, asset);
            return null;
        });
    }

    @Override
    public IO<Void> unregister(ID assetId) {
        Objects.requireNonNull(assetId);
        return IO.delay(() -> {
            assets.remove(assetId);
            return null;
        });
    }

    @Override
    public IO<Boolean> isRegistered(ID assetId) {
        Objects.requireNonNull(assetId);
        return IO.delay(() -> assets.containsKey(assetId));
    }

    /**
     * Assigns the initial owner to an asset.
     */
    public IO<Either<String, OwnershipRecord<ID, O>>> assignOwner(ID assetId, O owner, Instant now) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(now);

        OwnableRequest<ID, O> asset = assets.get(assetId);
        if (asset == null) {
            return IO.of(Either.left("Asset domain object not registered: " + assetId));
        }

        return coordinator.coordinate(
            assetId,
            "assign",
            optRecord -> {
                if (optRecord.isPresent() && optRecord.get().hasOwner()) {
                    return Either.left("Asset already has an active owner: " + optRecord.get().currentOwner());
                }
                return OwnershipRecord.assign(assetId, owner, asset, now);
            },
            Function.identity()
        );
    }

    /**
     * Transfers ownership from the current owner to a proposed owner.
     */
    public IO<Either<String, OwnershipRecord<ID, O>>> transferOwner(
        ID assetId, 
        O currentOwner, 
        O proposedOwner, 
        O actor, 
        String comment, 
        Instant now
    ) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(currentOwner);
        Objects.requireNonNull(proposedOwner);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        OwnableRequest<ID, O> asset = assets.get(assetId);
        if (asset == null) {
            return IO.of(Either.left("Asset domain object not registered: " + assetId));
        }

        return coordinator.coordinate(
            assetId,
            "transfer",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Ownership register not found for asset: " + assetId);
                }
                OwnershipRecord<ID, O> record = optRecord.get();
                Either<String, OwnershipEvent<ID, O>> eitherEvent = record.transfer(currentOwner, proposedOwner, actor, comment, asset, now);
                return eitherEvent.map(event -> new TransitionResult<>(record, event));
            },
            Function.identity()
        );
    }

    /**
     * Revokes ownership from the current owner.
     */
    public IO<Either<String, OwnershipRecord<ID, O>>> revokeOwner(
        ID assetId, 
        O currentOwner, 
        O actor, 
        String reason, 
        Instant now
    ) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(currentOwner);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);

        OwnableRequest<ID, O> asset = assets.get(assetId);
        if (asset == null) {
            return IO.of(Either.left("Asset domain object not registered: " + assetId));
        }

        return coordinator.coordinate(
            assetId,
            "revoke",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Ownership register not found for asset: " + assetId);
                }
                OwnershipRecord<ID, O> record = optRecord.get();
                Either<String, OwnershipEvent<ID, O>> eitherEvent = record.revoke(currentOwner, actor, reason, asset, now);
                return eitherEvent.map(event -> new TransitionResult<>(record, event));
            },
            Function.identity()
        );
    }
}
