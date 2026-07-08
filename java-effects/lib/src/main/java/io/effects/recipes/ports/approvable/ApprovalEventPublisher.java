package io.effects.recipes.ports.approvable;

import io.effects.IO;

/**
 * Port representing external publication capabilities for approval events.
 */
public interface ApprovalEventPublisher {

    /**
     * Publish an approval event.
     */
    IO<Void> publish(ApprovalEvent event);
}
