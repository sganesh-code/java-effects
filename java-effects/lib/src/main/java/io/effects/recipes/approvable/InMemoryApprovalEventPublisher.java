package io.effects.recipes.approvable;

import io.effects.IO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An in-memory, thread-safe implementation of ApprovalEventPublisher for testing and auditing.
 */
final class InMemoryApprovalEventPublisher implements ApprovalEventPublisher {
    private final List<ApprovalEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public IO<Void> publish(ApprovalEvent event) {
        return IO.delay(() -> {
            events.add(event);
            return null;
        });
    }

    /**
     * Retrieves an immutable copy of the published events.
     */
    public List<ApprovalEvent> getPublishedEvents() {
        return new ArrayList<>(events);
    }

    /**
     * Clears the internal event log.
     */
    public void clear() {
        events.clear();
    }
}
