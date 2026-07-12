package io.effects.recipes.routable;

import io.effects.recipes.routable.models.*;
import io.effects.core.Either;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing the current routing status
 * and history of steps.
 * It is an Aggregate Root that completely owns state progressions and produces RoutableEvent occurrences.
 */
public final class RouteLedger<ID, H, C> {
    public enum Status { UNROUTED, ROUTED, REJECTED }

    private final ID workId;
    private Status status = Status.UNROUTED;
    private H handler = null;
    private final List<RoutingStep<H, C>> history = new ArrayList<>();

    public RouteLedger(ID workId) {
        this.workId = Objects.requireNonNull(workId);
    }

    public synchronized ID workId() { return workId; }
    public synchronized Status status() { return status; }
    public synchronized H currentHandler() { return handler; }
    public synchronized List<RoutingStep<H, C>> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }

    public synchronized boolean isTerminal() {
        return status == Status.REJECTED;
    }

    /**
     * Records a step and transitions state internally.
     */
    private synchronized void recordStep(RoutingStep<H, C> step, Status nextStatus, H nextHandler) {
        Objects.requireNonNull(step);
        Objects.requireNonNull(nextStatus);

        if (isTerminal()) {
            throw new IllegalStateException("Cannot register a routing step on a terminal route ledger: " + workId);
        }

        this.history.add(step);
        this.status = nextStatus;
        this.handler = nextHandler;
    }

    /**
     * Behavioral Factory: Creates a new route ledger.
     */
    public static <ID, H, C> RouteLedger<ID, H, C> initiate(ID workId) {
        return new RouteLedger<>(workId);
    }

    /**
     * Behavioral Transition: Routes work to a proposed initial handler.
     */
    public synchronized Either<String, RoutableEvent<ID, H>> route(
        H proposedHandler, 
        C comment, 
        RoutableRequest<ID, H, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(proposedHandler);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.UNROUTED) {
            return Either.left("Cannot route in current status: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateInitialRoute(this, proposedHandler, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        RoutingStep<H, C> step = new RoutingStep<>(
            UUID.randomUUID().toString(),
            proposedHandler,
            RoutingStep.Type.ROUTE,
            comment,
            now
        );
        recordStep(step, Status.ROUTED, proposedHandler);

        RoutableEvent<ID, H> event = new WorkRouted<>(workId, proposedHandler, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Reroutes work from current handler to a new handler.
     */
    public synchronized Either<String, RoutableEvent<ID, H>> reroute(
        H proposedHandler, 
        C comment, 
        RoutableRequest<ID, H, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(proposedHandler);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.ROUTED) {
            return Either.left("Cannot reroute in current status: " + status);
        }

        if (Objects.equals(handler, proposedHandler)) {
            return Either.left("Proposed handler is already the current handler: " + proposedHandler);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateReroute(this, handler, proposedHandler, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        H previousHandler = this.handler;

        RoutingStep<H, C> step = new RoutingStep<>(
            UUID.randomUUID().toString(),
            proposedHandler,
            RoutingStep.Type.REROUTE,
            comment,
            now
        );
        recordStep(step, Status.ROUTED, proposedHandler);

        RoutableEvent<ID, H> event = new WorkRerouted<>(workId, previousHandler, proposedHandler, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Rejects the routing workflow.
     */
    public synchronized Either<String, RoutableEvent<ID, H>> reject(
        C reason, 
        RoutableRequest<ID, H, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status == Status.REJECTED) {
            return Either.right(null); // Idempotent success
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateRejection(this, reason, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        RoutingStep<H, C> step = new RoutingStep<>(
            UUID.randomUUID().toString(),
            null, // No handler when rejected
            RoutingStep.Type.REJECT,
            reason,
            now
        );
        recordStep(step, Status.REJECTED, null);

        RoutableEvent<ID, H> event = new RoutingRejected<>(workId, reason.toString(), now);
        return Either.right(event);
    }
}
