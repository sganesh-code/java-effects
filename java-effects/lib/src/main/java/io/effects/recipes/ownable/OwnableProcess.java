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
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An Object-Oriented "Recipe" representing an Ownership Process Manager.
 * It coordinates routing messages, evaluating domain invariants, persistence, event emission, and telemetry.
 * 
 * In accordance with our architectural boundary, this process represents the monadic infrastructure
 * engine, and thus exposes purely monadic APIs (returning IO) to allow lazy, virtual-thread execution,
 * cancellation, and pipeline composition.
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
                OwnershipRecord record = optRecord.orElseGet(() -> new OwnershipRecord(assetId));

                if (record.hasOwner()) {
                    return IO.of(Either.<String, OwnershipRecord>left("Asset already has an active owner: " + record.currentOwnerId()));
                }

                // Invoke pure domain trial synchronously
                Either<String, Void> eitherValid = asset.evaluateInitialAssignment(ownerId, now);
                if (eitherValid.isLeft()) {
                    return IO.of(Either.<String, OwnershipRecord>left(eitherValid.getLeft()));
                }

                OwnershipStep step = new OwnershipStep(
                    UUID.randomUUID().toString(),
                    ownerId,
                    OwnershipStep.Type.ASSIGN,
                    "Initial ownership assignment",
                    now
                );
                record.recordTransfer(step, ownerId);

                OwnershipEvent event = new OwnershipAssigned(assetId, ownerId, now);

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
                if (!record.hasOwner()) {
                    return IO.of(Either.<String, OwnershipRecord>left("Cannot transfer: asset has no active owner"));
                }
                if (!record.currentOwnerId().equals(currentOwnerId)) {
                    return IO.of(Either.<String, OwnershipRecord>left("Current owner mismatch: expected " + record.currentOwnerId() + " but got " + currentOwnerId));
                }

                // Invoke pure domain trial synchronously
                Either<String, Void> eitherValid = asset.evaluateTransfer(record, currentOwnerId, proposedOwnerId, actorId, now);
                if (eitherValid.isLeft()) {
                    return IO.of(Either.<String, OwnershipRecord>left(eitherValid.getLeft()));
                }

                OwnershipStep step = new OwnershipStep(
                    UUID.randomUUID().toString(),
                    actorId,
                    OwnershipStep.Type.TRANSFER,
                    comment,
                    now
                );
                record.recordTransfer(step, proposedOwnerId);

                OwnershipEvent event = new OwnershipTransferred(assetId, currentOwnerId, proposedOwnerId, now);

                return repository.save(assetId, record)
                    .flatMap(v -> publisher.publish(event))
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
                if (!record.hasOwner()) {
                    return IO.of(Either.<String, OwnershipRecord>left("Cannot revoke: asset has no active owner"));
                }
                if (!record.currentOwnerId().equals(currentOwnerId)) {
                    return IO.of(Either.<String, OwnershipRecord>left("Current owner mismatch: expected " + record.currentOwnerId() + " but got " + currentOwnerId));
                }

                // Invoke pure domain trial synchronously
                Either<String, Void> eitherValid = asset.evaluateTransfer(record, currentOwnerId, null, actorId, now);
                if (eitherValid.isLeft()) {
                    return IO.of(Either.<String, OwnershipRecord>left(eitherValid.getLeft()));
                }

                OwnershipStep step = new OwnershipStep(
                    UUID.randomUUID().toString(),
                    actorId,
                    OwnershipStep.Type.REVOKE,
                    reason,
                    now
                );
                record.recordTransfer(step, null);

                OwnershipEvent event = new OwnershipRevoked(assetId, currentOwnerId, now);

                return repository.save(assetId, record)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("ownable", assetId + ":revoke"))
                    .flatMap(v -> telemetry.recordDuration("ownable", assetId, System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, OwnershipRecord>right(record));
            })
            .yield((startTime, optRecord, result) -> result);
    }
}
