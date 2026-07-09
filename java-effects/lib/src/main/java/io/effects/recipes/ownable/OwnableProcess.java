package io.effects.recipes.ownable;

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
 * An Object-Oriented "Recipe" representing an Ownership Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside OwnershipRecord).
 */
public final class OwnableProcess<ID, O> {
    private final StateRepository<ID, OwnershipRecord<ID, O>> repository;
    private final EventPublisher<OwnershipEvent<ID, O>> publisher;
    private final TelemetryPort telemetry;
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
    }

    /**
     * Registers a behavioral ownable request domain object.
     */
    public IO<Void> register(ID assetId, OwnableRequest<ID, O> asset) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(asset);
        return IO.delay(() -> {
            assets.put(assetId, asset);
            return null;
        });
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

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(assetId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isPresent() && optRecord.get().hasOwner()) {
                    return IO.of(Either.<String, OwnershipRecord<ID, O>>left("Asset already has an active owner: " + optRecord.get().currentOwner()));
                }

                // Delegate creation and transition to rich aggregate factory
                Either<String, TransitionResult<OwnershipRecord<ID, O>, OwnershipEvent<ID, O>>> assignResult = OwnershipRecord.assign(
                    assetId, owner, asset, now
                );

                if (assignResult.isLeft()) {
                    return IO.of(Either.<String, OwnershipRecord<ID, O>>left(assignResult.getLeft()));
                }

                TransitionResult<OwnershipRecord<ID, O>, OwnershipEvent<ID, O>> result = assignResult.getRight();
                OwnershipRecord<ID, O> record = result.aggregate();
                OwnershipEvent<ID, O> event = result.event();

                return repository.save(assetId, record)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("ownable", assetId.toString() + ":assign"))
                    .flatMap(v -> telemetry.recordDuration("ownable", assetId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, OwnershipRecord<ID, O>>right(record));
            })
            .yield((startTime, optRecord, result) -> result);
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

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(assetId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isEmpty()) {
                    return IO.of(Either.<String, OwnershipRecord<ID, O>>left("Ownership record not found for asset: " + assetId));
                }

                OwnershipRecord<ID, O> record = optRecord.get();

                // Delegate execution directly to rich aggregate!
                Either<String, OwnershipEvent<ID, O>> eitherEvent = record.transfer(currentOwner, proposedOwner, actor, comment, asset, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, OwnershipRecord<ID, O>>left(eitherEvent.getLeft()));
                }

                OwnershipEvent<ID, O> event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(assetId, record)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("ownable", assetId.toString() + ":transfer"))
                    .flatMap(v -> telemetry.recordDuration("ownable", assetId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, OwnershipRecord<ID, O>>right(record));
            })
            .yield((startTime, optRecord, result) -> result);
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

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(assetId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isEmpty()) {
                    return IO.of(Either.<String, OwnershipRecord<ID, O>>left("Ownership record not found for asset: " + assetId));
                }

                OwnershipRecord<ID, O> record = optRecord.get();

                // Delegate execution directly to rich aggregate!
                Either<String, OwnershipEvent<ID, O>> eitherEvent = record.revoke(currentOwner, actor, reason, asset, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, OwnershipRecord<ID, O>>left(eitherEvent.getLeft()));
                }

                OwnershipEvent<ID, O> event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(assetId, record)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("ownable", assetId.toString() + ":revoke"))
                    .flatMap(v -> telemetry.recordDuration("ownable", assetId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, OwnershipRecord<ID, O>>right(record));
            })
            .yield((startTime, optRecord, result) -> result);
    }
}