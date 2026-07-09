package io.effects.extensions;

import io.effects.IO;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Functional and monadic extension methods for {@link java.util.Map}.
 * Designed for use with Lombok's {@code @ExtensionMethod}.
 */
public final class MapExtensions {

    private MapExtensions() {}

    // Functor Operations on Values

    public static <K, V, R> Map<K, R> mapValues(Map<K, V> map, Function<? super V, ? extends R> fn) {
        Objects.requireNonNull(map, "map must not be null");
        Objects.requireNonNull(fn, "fn must not be null");
        Map<K, R> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            result.put(entry.getKey(), fn.apply(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    // Functor Operations on Keys

    public static <K, V, R> Map<R, V> mapKeys(Map<K, V> map, Function<? super K, ? extends R> fn) {
        Objects.requireNonNull(map, "map must not be null");
        Objects.requireNonNull(fn, "fn must not be null");
        Map<R, V> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            result.put(fn.apply(entry.getKey()), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    // Bifunctor Operations

    public static <K1, V1, K2, V2> Map<K2, V2> bimap(Map<K1, V1> map, Function<? super K1, ? extends K2> f, Function<? super V1, ? extends V2> g) {
        Objects.requireNonNull(map, "map must not be null");
        Objects.requireNonNull(f, "f must not be null");
        Objects.requireNonNull(g, "g must not be null");
        Map<K2, V2> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<K1, V1> entry : map.entrySet()) {
            result.put(f.apply(entry.getKey()), g.apply(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    // Foldable Operations

    public static <K, V, B> B foldLeft(Map<K, V> map, B zero, java.util.function.BiFunction<B, Map.Entry<K, V>, B> f) {
        Objects.requireNonNull(map, "map must not be null");
        Objects.requireNonNull(f, "f must not be null");
        B acc = zero;
        for (Map.Entry<K, V> entry : map.entrySet()) {
            acc = f.apply(acc, entry);
        }
        return acc;
    }

    // Effect Sequence & Traversal on Values

    public static <K, V, R> IO<Map<K, R>> traverseValuesIO(Map<K, V> map, Function<? super V, ? extends IO<R>> fn) {
        Objects.requireNonNull(map, "map must not be null");
        Objects.requireNonNull(fn, "fn must not be null");
        IO<Map<K, R>> acc = IO.of(new LinkedHashMap<>());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            K key = entry.getKey();
            IO<R> ioR = fn.apply(entry.getValue());
            acc = acc.flatMap(resMap -> ioR.map(r -> {
                Map<K, R> newMap = new LinkedHashMap<>(resMap);
                newMap.put(key, r);
                return newMap;
            }));
        }
        return acc.map(Collections::unmodifiableMap);
    }

    public static <K, V> IO<Map<K, V>> sequenceValuesIO(Map<K, ? extends IO<V>> map) {
        return traverseValuesIO(map, Function.identity());
    }
}
