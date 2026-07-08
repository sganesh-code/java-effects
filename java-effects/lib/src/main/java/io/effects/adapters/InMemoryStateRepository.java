package io.effects.adapters;

import io.effects.IO;
import io.effects.ports.StateRepository;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A generalized, thread-safe, in-memory implementation of StateRepository for testing.
 */
public final class InMemoryStateRepository<K, V> implements StateRepository<K, V> {
    private final ConcurrentMap<K, V> storage = new ConcurrentHashMap<>();

    @Override
    public IO<Optional<V>> find(K key) {
        return IO.delay(() -> Optional.ofNullable(storage.get(key)));
    }

    @Override
    public IO<Void> save(K key, V value) {
        return IO.delay(() -> {
            storage.put(key, value);
            return null;
        });
    }

    @Override
    public IO<Void> remove(K key) {
        return IO.delay(() -> {
            storage.remove(key);
            return null;
        });
    }
}
