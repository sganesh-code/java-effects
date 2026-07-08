package io.effects.recipes.reservable;

import io.effects.IO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An in-memory, thread-safe implementation of EventPublisher for testing and auditing.
 */
public final class InMemoryEventPublisher implements EventPublisher {
    private final List<ReservationEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    public IO<Void> publish(ReservationEvent event) {
        return IO.delay(() -> {
            events.add(event);
            return null;
        });
    }

    /**
     * Retrieves an immutable copy of the published events.
     */
    public List<ReservationEvent> getPublishedEvents() {
        return new ArrayList<>(events);
    }

    /**
     * Clears the internal event log.
     */
    public void clear() {
        events.clear();
    }
}
