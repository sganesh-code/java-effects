package io.effects.ports;

import io.effects.core.IO;

/**
 * A generalized, type-safe port representing external publication capabilities.
 */
public interface EventPublisher<E> {

    /**
     * Publish a historical domain fact.
     */
    IO<Void> publish(E event);
}
