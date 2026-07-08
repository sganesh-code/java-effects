package io.effects.recipes.ports.approvable;

import java.time.Instant;

/**
 * A historical domain fact representing a lifecycle event of the approval recipe.
 */
public interface ApprovalEvent {

    /**
     * The unique identifier of the target request.
     */
    String requestId();

    /**
     * The timestamp of when the event occurred.
     */
    Instant occurredAt();
}
