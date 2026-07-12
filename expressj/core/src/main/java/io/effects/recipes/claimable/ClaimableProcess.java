package io.effects.recipes.claimable;

import io.effects.recipes.claimable.models.*;
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
 * An Object-Oriented "Recipe" representing a Claimable Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside ClaimLedger).
 */
public final class ClaimableProcess<ID, V, C> implements Recipe<ID, ClaimableRequest<ID, V, C>> {
    private final StateRepository<ID, ClaimLedger<ID, V, C>> repository;
    private final EventPublisher<ClaimableEvent<ID>> publisher;
    private final TelemetryPort telemetry;
    private final ProcessCoordinator<ID, ClaimLedger<ID, V, C>, ClaimableEvent<ID>> coordinator;
    private final ConcurrentMap<ID, ClaimableRequest<ID, V, C>> requests = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public ClaimableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public ClaimableProcess(
        StateRepository<ID, ClaimLedger<ID, V, C>> repository,
        EventPublisher<ClaimableEvent<ID>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.coordinator = new ProcessCoordinator<>(repository, publisher, telemetry, "claimable");
    }

    @Override
    public IO<Void> register(ID claimId, ClaimableRequest<ID, V, C> request) {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            requests.put(claimId, request);
            return null;
        });
    }

    @Override
    public IO<Void> unregister(ID claimId) {
        Objects.requireNonNull(claimId);
        return IO.delay(() -> {
            requests.remove(claimId);
            return null;
        });
    }

    @Override
    public IO<Boolean> isRegistered(ID claimId) {
        Objects.requireNonNull(claimId);
        return IO.delay(() -> requests.containsKey(claimId));
    }

    /**
     * Coordinates the filing of an initial claim.
     */
    public IO<Either<String, ClaimLedger<ID, V, C>>> file(ID claimId, String claimantId, C comment, Instant now) {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(claimantId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        ClaimableRequest<ID, V, C> request = requests.get(claimId);
        if (request == null) {
            return IO.of(Either.left("Claimable request domain object not registered: " + claimId));
        }

        return coordinator.coordinate(
            claimId,
            "file",
            optRecord -> {
                if (optRecord.isPresent() && optRecord.get().status() != null) {
                    return Either.left("Claim has already been filed: " + claimId);
                }
                ClaimLedger<ID, V, C> ledger = optRecord.orElseGet(() -> ClaimLedger.initiate(claimId));
                Either<String, ClaimableEvent<ID>> eitherEvent = ledger.file(claimantId, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates moving the claim to active review.
     */
    public IO<Either<String, ClaimLedger<ID, V, C>>> review(ID claimId, String reviewerId, V validatorRole, C comment, Instant now) {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(reviewerId);
        Objects.requireNonNull(validatorRole);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        ClaimableRequest<ID, V, C> request = requests.get(claimId);
        if (request == null) {
            return IO.of(Either.left("Claimable request domain object not registered: " + claimId));
        }

        return coordinator.coordinate(
            claimId,
            "review",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Claim ledger not found: " + claimId);
                }
                ClaimLedger<ID, V, C> ledger = optRecord.get();
                Either<String, ClaimableEvent<ID>> eitherEvent = ledger.review(reviewerId, validatorRole, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates accepting the claim.
     */
    public IO<Either<String, ClaimLedger<ID, V, C>>> accept(ID claimId, String reviewerId, V validatorRole, C comment, Instant now) {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(reviewerId);
        Objects.requireNonNull(validatorRole);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        ClaimableRequest<ID, V, C> request = requests.get(claimId);
        if (request == null) {
            return IO.of(Either.left("Claimable request domain object not registered: " + claimId));
        }

        return coordinator.coordinate(
            claimId,
            "accept",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Claim ledger not found: " + claimId);
                }
                ClaimLedger<ID, V, C> ledger = optRecord.get();
                Either<String, ClaimableEvent<ID>> eitherEvent = ledger.accept(reviewerId, validatorRole, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates denying the claim.
     */
    public IO<Either<String, ClaimLedger<ID, V, C>>> deny(ID claimId, String reviewerId, V validatorRole, C comment, Instant now) {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(reviewerId);
        Objects.requireNonNull(validatorRole);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        ClaimableRequest<ID, V, C> request = requests.get(claimId);
        if (request == null) {
            return IO.of(Either.left("Claimable request domain object not registered: " + claimId));
        }

        return coordinator.coordinate(
            claimId,
            "deny",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Claim ledger not found: " + claimId);
                }
                ClaimLedger<ID, V, C> ledger = optRecord.get();
                Either<String, ClaimableEvent<ID>> eitherEvent = ledger.deny(reviewerId, validatorRole, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }

    /**
     * Coordinates disputing a terminal decision to reopen evaluation.
     */
    public IO<Either<String, ClaimLedger<ID, V, C>>> dispute(ID claimId, String actorId, C comment, Instant now) {
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(now);

        ClaimableRequest<ID, V, C> request = requests.get(claimId);
        if (request == null) {
            return IO.of(Either.left("Claimable request domain object not registered: " + claimId));
        }

        return coordinator.coordinate(
            claimId,
            "dispute",
            optRecord -> {
                if (optRecord.isEmpty()) {
                    return Either.left("Claim ledger not found: " + claimId);
                }
                ClaimLedger<ID, V, C> ledger = optRecord.get();
                Either<String, ClaimableEvent<ID>> eitherEvent = ledger.dispute(actorId, comment, request, now);
                return eitherEvent.map(event -> new TransitionResult<>(ledger, event));
            },
            Function.identity()
        );
    }
}
