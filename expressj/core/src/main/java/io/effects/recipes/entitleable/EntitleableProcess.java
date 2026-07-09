package io.effects.recipes.entitleable;

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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An Object-Oriented "Recipe" representing an Entitleable Process Manager.
 * It coordinates monadic persistence lookup, domain aggregation, and event publishing,
 * completely decoupled from business logic invariants (which reside inside EntitlementLedger).
 */
public final class EntitleableProcess<ID, G, C> {
    private final StateRepository<ID, EntitlementLedger<ID, G>> repository;
    private final EventPublisher<EntitlementEvent<ID>> publisher;
    private final TelemetryPort telemetry;
    private final ConcurrentMap<ID, EntitleableRequest<ID, G, C>> rules = new ConcurrentHashMap<>();

    /**
     * Default constructor uses the in-memory adapters for robust backward compatibility.
     */
    public EntitleableProcess() {
        this(new InMemoryStateRepository<>(), new InMemoryEventPublisher<>(), new NoOpTelemetryPort());
    }

    /**
     * Dependency injection constructor to configure custom ports/adapters at runtime.
     */
    public EntitleableProcess(
        StateRepository<ID, EntitlementLedger<ID, G>> repository,
        EventPublisher<EntitlementEvent<ID>> publisher,
        TelemetryPort telemetry
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    /**
     * Registers a behavioral entitlement request domain object containing custom rules.
     */
    public IO<Void> register(ID actorId, EntitleableRequest<ID, G, C> rule) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(rule);
        return IO.delay(() -> {
            rules.put(actorId, rule);
            return null;
        });
    }

    /**
     * Creates/Initiates a new entitlement ledger for an actor.
     */
    public IO<Void> initiate(ID actorId) {
        Objects.requireNonNull(actorId);
        return repository.save(actorId, new EntitlementLedger<>(actorId));
    }

    /**
     * Grants a new entitlement capability to an actor.
     */
    public IO<Either<String, EntitlementStep<G>>> grant(ID actorId, String grantorId, G grant, Instant now) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(grantorId);
        Objects.requireNonNull(grant);
        Objects.requireNonNull(now);

        EntitleableRequest<ID, G, C> rule = rules.get(actorId);
        if (rule == null) {
            return IO.of(Either.left("Entitleable request domain object not registered for actor: " + actorId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(actorId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, EntitlementStep<G>>left("Entitlement ledger not found for actor: " + actorId));
                }

                EntitlementLedger<ID, G> ledger = optLedger.get();
                String stepId = UUID.randomUUID().toString();

                Either<String, EntitlementStep<G>> tryGrantResult = ledger.grant(stepId, grantorId, grant, rule, now);
                if (tryGrantResult.isLeft()) {
                    return IO.of(Either.<String, EntitlementStep<G>>left(tryGrantResult.getLeft()));
                }

                EntitlementStep<G> step = tryGrantResult.getRight();
                EntitlementEvent<ID> event = new EntitlementEvent.EntitlementGranted<>(actorId, stepId, grant, now);

                return repository.save(actorId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("entitleable", actorId.toString() + ":grant"))
                    .flatMap(v -> telemetry.recordDuration("entitleable", actorId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, EntitlementStep<G>>right(step));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Revokes an active entitlement grant from an actor.
     */
    public IO<Either<String, EntitlementStep<G>>> revoke(ID actorId, String grantorId, String stepId, Instant now) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(grantorId);
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(now);

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(actorId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, EntitlementStep<G>>left("Entitlement ledger not found for actor: " + actorId));
                }

                EntitlementLedger<ID, G> ledger = optLedger.get();

                Either<String, EntitlementStep<G>> tryRevokeResult = ledger.revoke(stepId, grantorId, now);
                if (tryRevokeResult.isLeft()) {
                    return IO.of(Either.<String, EntitlementStep<G>>left(tryRevokeResult.getLeft()));
                }

                EntitlementStep<G> step = tryRevokeResult.getRight();
                EntitlementEvent<ID> event = new EntitlementEvent.EntitlementRevoked<>(actorId, stepId, step.grant(), now);

                return repository.save(actorId, ledger)
                    .flatMap(v -> publisher.publish(event))
                    .flatMap(v -> telemetry.recordSuccess("entitleable", actorId.toString() + ":revoke"))
                    .flatMap(v -> telemetry.recordDuration("entitleable", actorId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> Either.<String, EntitlementStep<G>>right(step));
            })
            .yield((startTime, optLedger, result) -> result);
    }

    /**
     * Evaluates a dynamic permission capability check against the actor's active grants and context.
     */
    public IO<Either<String, Void>> check(ID actorId, G grant, C context, Instant now) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(grant);
        Objects.requireNonNull(context);
        Objects.requireNonNull(now);

        EntitleableRequest<ID, G, C> rule = rules.get(actorId);
        if (rule == null) {
            return IO.of(Either.left("Entitleable request domain object not registered for actor: " + actorId));
        }

        return ForIO.set(IO.delay(System::currentTimeMillis))
            .bind(startTime -> repository.find(actorId))
            .bind((startTime, optLedger) -> {
                if (optLedger.isEmpty()) {
                    return IO.of(Either.<String, Void>left("Entitlement ledger not found for actor: " + actorId));
                }

                EntitlementLedger<ID, G> ledger = optLedger.get();

                Either<String, Void> eitherAllowed = ledger.check(grant, context, rule, now);
                boolean allowed = eitherAllowed.isRight();

                EntitlementEvent<ID> event = new EntitlementEvent.EntitlementChecked<>(actorId, grant, context, allowed, now);

                IO<Void> telemetryPortRecord = allowed 
                    ? telemetry.recordSuccess("entitleable", actorId.toString() + ":check_allowed")
                    : telemetry.recordFailure("entitleable", actorId.toString() + ":check_denied", eitherAllowed.isLeft() ? eitherAllowed.getLeft() : "denied");

                return publisher.publish(event)
                    .flatMap(v -> telemetryPortRecord)
                    .flatMap(v -> telemetry.recordDuration("entitleable", actorId.toString(), System.currentTimeMillis() - startTime))
                    .map(v -> eitherAllowed);
            })
            .yield((startTime, optLedger, result) -> result);
    }
}