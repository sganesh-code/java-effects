package io.effects.adapters;

import io.effects.IO;
import io.effects.ports.EventPublisher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A generalized, thread-safe, in-memory implementation of EventPublisher for testing and auditing.
 */
public final class InMemoryEventPublisher<E> implements EventPublisher<E> {
    private final List<E> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public IO<Void> publish(E event) {
        return IO.delay(() -> {
            events.add(event);
            return null;
        });
    }

    /**
     * Retrieves an immutable copy of the published events.
     */
    public List<E> getPublishedEvents() {
        return new ArrayList<>(events);
    }

    /**
     * Clears the internal event log.
     */
    public void clear() {
        events.clear();
    }
}
