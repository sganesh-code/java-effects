package io.effects.recipes.claimable;

import io.effects.recipes.claimable.models.*;
import io.effects.core.Either;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A rich, non-anemic domain state ledger representing the current claim status
 * and history of review actions.
 * It is an Aggregate Root that completely owns state progressions and produces ClaimableEvent occurrences.
 */
public final class ClaimLedger<ID, V, C> {
    public enum Status { FILED, UNDER_REVIEW, ACCEPTED, DENIED, DISPUTED }

    private final ID claimId;
    private String claimantId = null;
    private Status status = null;
    private final List<ClaimStep<C>> history = new ArrayList<>();

    public ClaimLedger(ID claimId) {
        this.claimId = Objects.requireNonNull(claimId);
    }

    public synchronized ID claimId() { return claimId; }
    public synchronized String claimantId() { return claimantId; }
    public synchronized Status status() { return status; }
    public synchronized List<ClaimStep<C>> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }

    public synchronized boolean isTerminal() {
        return status == Status.ACCEPTED || status == Status.DENIED;
    }

    /**
     * Records a step and transitions state internally.
     */
    private synchronized void recordStep(ClaimStep<C> step, Status nextStatus) {
        Objects.requireNonNull(step);
        Objects.requireNonNull(nextStatus);

        this.history.add(step);
        this.status = nextStatus;
    }

    /**
     * Behavioral Factory: Creates a new claim ledger.
     */
    public static <ID, V, C> ClaimLedger<ID, V, C> initiate(ID claimId) {
        return new ClaimLedger<>(claimId);
    }

    /**
     * Behavioral Transition: Files the initial claim.
     */
    public synchronized Either<String, ClaimableEvent<ID>> file(
        String claimantId, 
        C comment, 
        ClaimableRequest<ID, V, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(claimantId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != null) {
            return Either.left("Claim has already been filed: " + claimId);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateFile(this, claimantId, comment, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        this.claimantId = claimantId;

        ClaimStep<C> step = new ClaimStep<>(
            UUID.randomUUID().toString(),
            claimantId,
            ClaimStep.Type.FILE,
            comment,
            now
        );
        recordStep(step, Status.FILED);

        ClaimableEvent<ID> event = new ClaimFiled<>(claimId, claimantId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Puts the claim under active review.
     */
    public synchronized Either<String, ClaimableEvent<ID>> review(
        String reviewerId, 
        V validatorRole, 
        C comment, 
        ClaimableRequest<ID, V, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(reviewerId);
        Objects.requireNonNull(validatorRole);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.FILED && status != Status.DISPUTED) {
            return Either.left("Claim must be in FILED or DISPUTED status to initiate review, currently: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateReview(this, reviewerId, validatorRole, comment, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        ClaimStep<C> step = new ClaimStep<>(
            UUID.randomUUID().toString(),
            reviewerId,
            ClaimStep.Type.REVIEW,
            comment,
            now
        );
        recordStep(step, Status.UNDER_REVIEW);

        ClaimableEvent<ID> event = new ClaimUnderReview<>(claimId, reviewerId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Accepts the claim.
     */
    public synchronized Either<String, ClaimableEvent<ID>> accept(
        String reviewerId, 
        V validatorRole, 
        C comment, 
        ClaimableRequest<ID, V, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(reviewerId);
        Objects.requireNonNull(validatorRole);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.UNDER_REVIEW) {
            return Either.left("Claim must be UNDER_REVIEW to accept, currently: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateDecision(this, reviewerId, validatorRole, true, comment, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        ClaimStep<C> step = new ClaimStep<>(
            UUID.randomUUID().toString(),
            reviewerId,
            ClaimStep.Type.ACCEPT,
            comment,
            now
        );
        recordStep(step, Status.ACCEPTED);

        ClaimableEvent<ID> event = new ClaimAccepted<>(claimId, reviewerId, now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Denies the claim.
     */
    public synchronized Either<String, ClaimableEvent<ID>> deny(
        String reviewerId, 
        V validatorRole, 
        C comment, 
        ClaimableRequest<ID, V, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(reviewerId);
        Objects.requireNonNull(validatorRole);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.UNDER_REVIEW) {
            return Either.left("Claim must be UNDER_REVIEW to deny, currently: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateDecision(this, reviewerId, validatorRole, false, comment, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        ClaimStep<C> step = new ClaimStep<>(
            UUID.randomUUID().toString(),
            reviewerId,
            ClaimStep.Type.DENY,
            comment,
            now
        );
        recordStep(step, Status.DENIED);

        ClaimableEvent<ID> event = new ClaimDenied<>(claimId, reviewerId, comment.toString(), now);
        return Either.right(event);
    }

    /**
     * Behavioral Transition: Disputes a terminal decision and reopens.
     */
    public synchronized Either<String, ClaimableEvent<ID>> dispute(
        String actorId, 
        C comment, 
        ClaimableRequest<ID, V, C> request, 
        Instant now
    ) {
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(comment);
        Objects.requireNonNull(request);
        Objects.requireNonNull(now);

        if (status != Status.ACCEPTED && status != Status.DENIED) {
            return Either.left("Claim must be in a terminal status (ACCEPTED/DENIED) to dispute, currently: " + status);
        }

        // Domain validation (double dispatch)
        Either<String, Void> eitherValid = request.evaluateDispute(this, actorId, comment, now);
        if (eitherValid.isLeft()) {
            return Either.left(eitherValid.getLeft());
        }

        ClaimStep<C> step = new ClaimStep<>(
            UUID.randomUUID().toString(),
            actorId,
            ClaimStep.Type.DISPUTE,
            comment,
            now
        );
        recordStep(step, Status.DISPUTED);

        ClaimableEvent<ID> event = new ClaimDisputed<>(claimId, actorId, comment.toString(), now);
        return Either.right(event);
    }
}
