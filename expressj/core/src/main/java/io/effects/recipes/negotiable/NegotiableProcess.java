package io.effects.recipes.negotiable;

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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * An Object-Oriented "Recipe" representing a Negotiable Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside NegotiationLedger).
 */
public final class NegotiableProcess<ID, P> implements Recipe<ID, NegotiableRequest<ID, P>> {
    private final StateRepository<ID, NegotiationLedger<ID, P>> repository;
    private final EventPublisher<NegotiationEvent<ID>> publisher;
    private final TelemetryPort telemetry;
    private final ProcessCoordinator<ID, NegotiationLedger<ID, P>, NegotiationEvent<ID>> coordinator;
    private final ConcurrentMap<ID, NegotiableRequest<ID, P>> sessions = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public NegotiableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public NegotiableProcess(
        StateRepository<ID, NegotiationLedger<ID, P>> repository,
        EventPublisher<NegotiationEvent<ID>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.coordinator = new ProcessCoordinator<>(repository, publisher, telemetry, "negotiable");
    }

    /**
     * Registers a behavioral negotiable request domain object containing custom rules.
     */
    @Override
    public IO<Void> register(ID sessionId, NegotiableRequest<ID, P> request) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(request);
        return IO.delay(() -> {
            sessions.put(sessionId, request);
            return null;
        });
    }

    @Override
    public IO<Void> unregister(ID sessionId) {
        Objects.requireNonNull(sessionId);
        return IO.delay(() -> {
            sessions.remove(sessionId);
            return null;
        });
    }

    @Override
    public IO<Boolean> isRegistered(ID sessionId) {
        Objects.requireNonNull(sessionId);
        return IO.delay(() -> sessions.containsKey(sessionId));
    }

    /**
     * Creates/Initiates a new negotiation ledger.
     */
    public IO<Void> initiate(ID sessionId) {
        Objects.requireNonNull(sessionId);
        return repository.save(sessionId, new NegotiationLedger<>(sessionId));
    }

    /**
     * Submits an initial offer.
     */
    public IO<Either<String, NegotiationLedger<ID, P>>> makeOffer(ID sessionId, String actorId, P proposal, Instant now) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(proposal);
        Objects.requireNonNull(now);

        NegotiableRequest<ID, P> request = sessions.get(sessionId);
        if (request == null) {
            return IO.of(Either.left("Negotiation request domain object not registered: " + sessionId));
        }

        return coordinator.coordinate(
            sessionId,
            "offer",
            optLedger -> {
                if (optLedger.isEmpty()) {
                    return Either.left("Negotiation ledger not found: " + sessionId);
                }
                NegotiationLedger<ID, P> ledger = optLedger.get();
                String stepId = UUID.randomUUID().toString();
                Either<String, NegotiationStep<P>> tryOfferResult = ledger.makeOffer(stepId, actorId, proposal, request, now);
                return tryOfferResult.map(step -> new TransitionResult<>(ledger, new NegotiationEvent.OfferMade<>(sessionId, actorId, proposal, now)));
            },
            Function.identity()
        );
    }

    /**
     * Submits a counter-offer.
     */
    public IO<Either<String, NegotiationLedger<ID, P>>> makeCounter(ID sessionId, String actorId, P proposal, Instant now) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(proposal);
        Objects.requireNonNull(now);

        NegotiableRequest<ID, P> request = sessions.get(sessionId);
        if (request == null) {
            return IO.of(Either.left("Negotiation request domain object not registered: " + sessionId));
        }

        return coordinator.coordinate(
            sessionId,
            "counter",
            optLedger -> {
                if (optLedger.isEmpty()) {
                    return Either.left("Negotiation ledger not found: " + sessionId);
                }
                NegotiationLedger<ID, P> ledger = optLedger.get();
                String stepId = UUID.randomUUID().toString();
                Either<String, NegotiationStep<P>> tryCounterResult = ledger.makeCounter(stepId, actorId, proposal, request, now);
                return tryCounterResult.map(step -> new TransitionResult<>(ledger, new NegotiationEvent.CounterOfferMade<>(sessionId, actorId, proposal, now)));
            },
            Function.identity()
        );
    }

    /**
     * Accepts the current active proposal.
     */
    public IO<Either<String, NegotiationLedger<ID, P>>> accept(ID sessionId, String actorId, Instant now) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(now);

        NegotiableRequest<ID, P> request = sessions.get(sessionId);
        if (request == null) {
            return IO.of(Either.left("Negotiation request domain object not registered: " + sessionId));
        }

        return coordinator.coordinate(
            sessionId,
            "accept",
            optLedger -> {
                if (optLedger.isEmpty()) {
                    return Either.left("Negotiation ledger not found: " + sessionId);
                }
                NegotiationLedger<ID, P> ledger = optLedger.get();
                String stepId = UUID.randomUUID().toString();
                Either<String, NegotiationStep<P>> tryAcceptResult = ledger.accept(stepId, actorId, request, now);
                return tryAcceptResult.map(step -> new TransitionResult<>(ledger, new NegotiationEvent.NegotiationAccepted<>(sessionId, actorId, now)));
            },
            Function.identity()
        );
    }

    /**
     * Withdraws from active negotiation.
     */
    public IO<Either<String, NegotiationLedger<ID, P>>> withdraw(ID sessionId, String actorId, Instant now) {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(now);

        return coordinator.coordinate(
            sessionId,
            "withdraw",
            optLedger -> {
                if (optLedger.isEmpty()) {
                    return Either.left("Negotiation ledger not found: " + sessionId);
                }
                NegotiationLedger<ID, P> ledger = optLedger.get();
                String stepId = UUID.randomUUID().toString();
                Either<String, NegotiationStep<P>> tryWithdrawResult = ledger.withdraw(stepId, actorId, now);
                return tryWithdrawResult.map(step -> new TransitionResult<>(ledger, new NegotiationEvent.NegotiationWithdrawn<>(sessionId, actorId, now)));
            },
            Function.identity()
        );
    }
}
