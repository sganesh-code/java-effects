package io.effects.recipes.ports.reservable;

import io.effects.IO;

/**
 * Port representing external publication capabilities for reservation-lifecycle events.
 */
public interface EventPublisher {

    /**
     * Publish a historical domain fact.
     */
    IO<Void> publish(ReservationEvent event);
}
