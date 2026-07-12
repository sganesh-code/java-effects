package io.effects.recipes.negotiable;

import io.effects.recipes.negotiable.models.*;

import io.effects.core.Either;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A rich, non-anemic domain state ledger representing an active bargaining session.
 * It is an Aggregate Root that encapsulates sequential turn-taking invariants and executes double-dispatch.
 */
public final class NegotiationLedger<ID, P> {
    public enum Status { INITIAL, PENDING, AGREED, WITHDRAWN }

    private final ID sessionId;
    private Status status = Status.INITIAL;
    private final List<NegotiationStep<P>> history = new ArrayList<>();

    public NegotiationLedger(ID sessionId) {
        this.sessionId = Objects.requireNonNull(sessionId);
    }

    public synchronized ID sessionId() {
        return sessionId;
    }

    public synchronized Status status() {
        return status;
    }

    public synchronized List<NegotiationStep<P>> history() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public synchronized boolean isTerminal() {
        return status == Status.AGREED || status == Status.WITHDRAWN;
    }

    private synchronized void recordStep(NegotiationStep<P> step) {
        history.add(step);
    }

    /**
     * Behavioral Transition: Submits the initial offer to initiate the negotiation session.
     */
    public synchronized Either<String, NegotiationStep<P>> makeOffer(
        String stepId, 
        String actorId, 
        P proposal, 
        NegotiableRequest<ID, P> request, 
        Instant now
    ) {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(proposal);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.INITIAL) {
            return Either.left("Cannot make initial offer: session has already been initiated (current status: " + status + ")");
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateOffer(this, actorId, proposal, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        NegotiationStep<P> step = new NegotiationStep<>(stepId, actorId, NegotiationStep.Type.OFFER, proposal, now);
        recordStep(step);
        this.status = Status.PENDING;
        return Either.right(step);
    }

    /**
     * Behavioral Transition: Submits a counter-offer, enforcing sequential turn-taking invariants.
     */
    public synchronized Either<String, NegotiationStep<P>> makeCounter(
        String stepId, 
        String actorId, 
        P proposal, 
        NegotiableRequest<ID, P> request, 
        Instant now
    ) {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(proposal);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.PENDING) {
            return Either.left("Cannot make counter-proposal in current status: " + status);
        }

        // Turn Invariant: Counter-proposals can only be submitted by parties different from the sender of the active offer.
        String lastActorId = history.get(history.size() - 1).actorId();
        if (lastActorId.equals(actorId)) {
            return Either.left("Cannot submit counter-proposal: it is not your turn to counter (awaiting response from other party)");
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateCounter(this, actorId, proposal, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        NegotiationStep<P> step = new NegotiationStep<>(stepId, actorId, NegotiationStep.Type.COUNTER, proposal, now);
        recordStep(step);
        return Either.right(step);
    }

    /**
     * Behavioral Transition: Accepts the current active proposal, finalizing the agreement.
     */
    public synchronized Either<String, NegotiationStep<P>> accept(
        String stepId, 
        String actorId, 
        NegotiableRequest<ID, P> request, 
        Instant now
    ) {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.PENDING) {
            return Either.left("Cannot accept: no active proposal found (current status: " + status + ")");
        }

        // Turn Invariant: cannot accept your own proposal
        String lastActorId = history.get(history.size() - 1).actorId();
        if (lastActorId.equals(actorId)) {
            return Either.left("Cannot accept your own proposal");
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateAcceptance(this, actorId, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        P activeProposal = history.get(history.size() - 1).proposal();

        NegotiationStep<P> step = new NegotiationStep<>(stepId, actorId, NegotiationStep.Type.ACCEPT, activeProposal, now);
        recordStep(step);
        this.status = Status.AGREED;
        return Either.right(step);
    }

    /**
     * Behavioral Transition: Withdraws from active negotiation.
     */
    public synchronized Either<String, NegotiationStep<P>> withdraw(
        String stepId, 
        String actorId, 
        Instant now
    ) {
        Objects.requireNonNull(stepId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(now);

        if (isTerminal()) {
            return Either.left("Cannot withdraw: negotiation session is already terminal (current status: " + status + ")");
        }

        P activeProposal = history.isEmpty() ? null : history.get(history.size() - 1).proposal();

        NegotiationStep<P> step = new NegotiationStep<>(stepId, actorId, NegotiationStep.Type.WITHDRAW, activeProposal, now);
        recordStep(step);
        this.status = Status.WITHDRAWN;
        return Either.right(step);
    }
}