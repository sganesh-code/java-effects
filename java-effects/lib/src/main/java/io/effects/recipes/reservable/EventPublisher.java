package io.effects.recipes.reservable;

import io.effects.IO;

/**
 * A Port (Interface) representing the event-driven publication and orchestration capability.
 * All operations return lazy monadic IO blocks to preserve effect purity.
 */
public interface EventPublisher {

    /**
     * Publishes a reservation lifecycle event.
     */
    IO<Void> publish(ReservationEvent event);
}
