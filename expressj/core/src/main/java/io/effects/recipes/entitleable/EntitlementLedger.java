package io.effects.recipes.entitleable;

import io.effects.recipes.entitleable.models.*;

import io.effects.core.Either;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A rich, domain state ledger representing an actor's active entitlement grants and adjustment history.
 * It is an Aggregate Root that encapsulates all capability invariants and manages active mappings.
 */
public final class EntitlementLedger<ID, G> {
    private final ID actorId;
    private final ConcurrentMap<String, EntitlementStep<G>> activeGrants = new ConcurrentHashMap<>();
    private final List<EntitlementStep<G>> history = new ArrayList<>();

    public EntitlementLedger(ID actorId) {
        this.actorId = Objects.requireNonNull(actorId);
    }

    public synchronized ID actorId() {
        return actorId;
    }

    public synchronized List<EntitlementStep<G>> activeGrants() {
        return List.copyOf(activeGrants.values());
    }

    public synchronized List<EntitlementStep<G>> history() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    /**
     * Behavioral Transition: Grants an entitlement to this actor, checking rules via double-dispatch.
     */
    public synchronized Either<String, EntitlementStep<G>> grant(
        String stepId, 
        String grantorId, 
        G grant, 
        EntitleableRequest<ID, G, ?> request, 
        Instant now
    ) {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(grantorId);
        Objects.requireNonNull(grant);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateGrant(this, grant, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        EntitlementStep<G> step = new EntitlementStep<>(stepId, grantorId, EntitlementStep.Type.GRANT, grant, now);
        activeGrants.put(stepId, step);
        history.add(step);
        return Either.right(step);
    }

    /**
     * Behavioral Transition: Revokes an active entitlement grant.
     */
    public synchronized Either<String, EntitlementStep<G>> revoke(
        String stepId, 
        String grantorId, 
        Instant now
    ) {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(grantorId);
        Objects.requireNonNull(now);

        EntitlementStep<G> active = activeGrants.remove(stepId);
        if (active == null) {
            return Either.left("Active entitlement grant not found with step ID: " + stepId);
        }

        EntitlementStep<G> step = new EntitlementStep<>(
            UUID.randomUUID().toString(),
            grantorId,
            EntitlementStep.Type.REVOKE,
            active.grant(),
            now
        );
        history.add(step);
        return Either.right(step);
    }

    /**
     * Behavioral Transition: Evaluates an entitlement check request.
     */
    public synchronized <C> Either<String, Void> check(
        G grant, 
        C context, 
        EntitleableRequest<ID, G, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(grant);
        Objects.requireNonNull(context);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        return request.evaluateCheck(this, grant, context, now);
    }
}