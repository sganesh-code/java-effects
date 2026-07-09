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
public final class OwnableProcess {
    private final StateRepository<String, OwnershipRecord> repository;
    private final EventPublisher<OwnershipEvent> publisher;
    private final TelemetryPort telemetry;
    private final ConcurrentMap<String, OwnableRequest> assets = new ConcurrentHashMap<>();

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
        StateRepository<String, OwnershipRecord> repository,
        EventPublisher<OwnershipEvent> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    /**
     * Registers a behavioral ownable request domain object.
     */
    public IO<Void> register(String assetId, OwnableRequest asset) {
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
    public IO<Either<String, OwnershipRecord>> assignOwner(String assetId, String ownerId, Instant now) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(now);

        OwnableRequest asset = assets.get(assetId);
        if (asset == null) {
            return IO.of(Either.left("Asset domain object not registered: " + assetId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(assetId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isPresent() && optRecord.get().hasOwner()) {
                    return IO.of(Either.<String, OwnershipRecord>left("Asset already has an active owner: " + optRecord.get().currentOwnerId()));
                }

                // Delegate creation and transition to rich aggregate factory
                Either<String, TransitionResult<OwnershipRecord, OwnershipEvent>> assignResult = OwnershipRecord.assign(
                    assetId, ownerId, asset, now
                );

                if (assignResult.isLeft()) {
                    return IO.of(Either.<String, OwnershipRecord>left(assignResult.getLeft()));
                }

                TransitionResult<OwnershipRecord, OwnershipEvent> result = assignResult.getRight();
                OwnershipRecord record = result.aggregate();
                OwnershipEvent event = result.event();

                return repository.save(assetId, record)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("ownable", assetId + ":assign"))
                    .flatMap(v -> telemetry.recordDuration("ownable", assetId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, OwnershipRecord>right(record));
            })
            .yield((startTime, optRecord, result) -> result);
    }

    /**
     * Transfers ownership from the current owner to a proposed owner.
     */
    public IO<Either<String, OwnershipRecord>> transferOwner(
        String assetId, 
        String currentOwnerId, 
        String proposedOwnerId, 
        String actorId, 
        String comment, 
        Instant now
    ) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(currentOwnerId);
        Objects.requireNonNull(proposedOwnerId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        OwnableRequest asset = assets.get(assetId);
        if (asset == null) {
            return IO.of(Either.left("Asset domain object not registered: " + assetId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(assetId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isEmpty()) {
                    return IO.of(Either.<String, OwnershipRecord>left("Ownership record not found for asset: " + assetId));
                }

                OwnershipRecord record = optRecord.get();

                // Delegate execution directly to rich aggregate!
                Either<String, OwnershipEvent> eitherEvent = record.transfer(currentOwnerId, proposedOwnerId, actorId, comment, asset, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, OwnershipRecord>left(eitherEvent.getLeft()));
                }

                OwnershipEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(assetId, record)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("ownable", assetId + ":transfer"))
                    .flatMap(v -> telemetry.recordDuration("ownable", assetId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, OwnershipRecord>right(record));
            })
            .yield((startTime, optRecord, result) -> result);
    }

    /**
     * Revokes ownership from the current owner.
     */
    public IO<Either<String, OwnershipRecord>> revokeOwner(
        String assetId, 
        String currentOwnerId, 
        String actorId, 
        String reason, 
        Instant now
    ) {
        Objects.requireNonNull(assetId);
        Objects.requireNonNull(currentOwnerId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(now);

        OwnableRequest asset = assets.get(assetId);
        if (asset == null) {
            return IO.of(Either.left("Asset domain object not registered: " + assetId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(assetId))
            .bind((startTime, optRecord) -> {
                if (optRecord.isEmpty()) {
                    return IO.of(Either.<String, OwnershipRecord>left("Ownership record not found for asset: " + assetId));
                }

                OwnershipRecord record = optRecord.get();

                // Delegate execution directly to rich aggregate!
                Either<String, OwnershipEvent> eitherEvent = record.revoke(currentOwnerId, actorId, reason, asset, now);
                if (eitherEvent.isLeft()) {
                    return IO.of(Either.<String, OwnershipRecord>left(eitherEvent.getLeft()));
                }

                OwnershipEvent event = eitherEvent.getRight();
                IO<Void> publishIO = event != null ? publisher.publish(event) : IO.of(null);

                return repository.save(assetId, record)
                    .flatMap(v -> publishIO)
                    .flatMap(v -> telemetry.recordSuccess("ownable", assetId + ":revoke"))
                    .flatMap(v -> telemetry.recordDuration("ownable", assetId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, OwnershipRecord>right(record));
            })
            .yield((startTime, optRecord, result) -> result);
    }
}
