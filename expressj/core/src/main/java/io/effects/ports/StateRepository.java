package io.effects.ports;

import io.effects.IO;
import java.util.Optional;

/**
 * A generalized, type-safe persistence port for saving and loading states by their identifier.
 */
public interface StateRepository<K, V> {

    /**
     * Finds and loads the state value by its identifier key.
     */
    IO<Optional<V>> find(K key);

    /**
     * Saves or updates the state value associated with the key.
     */
    IO<Void> save(K key, V value);

    /**
     * Removes the state value associated with the key.
     */
    IO<Void> remove(K key);
}
