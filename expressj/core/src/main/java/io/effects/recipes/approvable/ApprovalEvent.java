package io.effects.recipes.approvable;

import io.effects.recipes.approvable.models.*;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the approval recipe.
 */
public interface ApprovalEvent<ID, A> {

    /**
     * The unique identifier of the target request.
     */
    ID requestId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}