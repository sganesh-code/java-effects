package io.effects.extensions;

import io.effects.Either;
import io.effects.IO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Functional and monadic extension methods for {@link java.util.Set}.
 * Designed for use with Lombok's {@code @ExtensionMethod}.
 * Implements the Pragmatic Hybrid Approach: ergonomic functors combined with effect-bridging.
 */
public final class SetExtensions {

    private SetExtensions() {}

    // Ergonomic Functor Operations

    public static <T, R> Set<R> map(Set<T> set, Function<? super T, ? extends R> fn) {
        Objects.requireNonNull(set, "set must not be null");
        Objects.requireNonNull(fn, "fn must not be null");
        Set<R> result = new LinkedHashSet<>(set.size());
        for (T item : set) {
            result.add(fn.apply(item));
        }
        return Collections.unmodifiableSet(result);
    }

    public static <T, R> Set<R> as(Set<T> set, R value) {
        return map(set, t -> value);
    }

    public static <T> Set<Void> voided(Set<T> set) {
        return map(set, t -> null);
    }

    // Foldable Operations

    public static <T, B> B foldLeft(Set<T> set, B zero, java.util.function.BiFunction<B, ? super T, B> f) {
        Objects.requireNonNull(set, "set must not be null");
        Objects.requireNonNull(f, "f must not be null");
        B acc = zero;
        for (T item : set) {
            acc = f.apply(acc, item);
        }
        return acc;
    }

    public static <T, B> B foldRight(Set<T> set, B zero, java.util.function.BiFunction<? super T, B, B> f) {
        Objects.requireNonNull(set, "set must not be null");
        Objects.requireNonNull(f, "f must not be null");
        List<T> list = new ArrayList<>(set);
        B acc = zero;
        for (int i = list.size() - 1; i >= 0; i--) {
            acc = f.apply(list.get(i), acc);
        }
        return acc;
    }

    // Effect Sequence & Traversal Operations

    public static <T, R> IO<Set<R>> traverseIO(Set<T> set, Function<? super T, ? extends IO<R>> fn) {
        Objects.requireNonNull(set, "set must not be null");
        Objects.requireNonNull(fn, "fn must not be null");
        IO<Set<R>> acc = IO.of(new LinkedHashSet<>());
        for (T item : set) {
            IO<R> ioR = fn.apply(item);
            acc = acc.flatMap(resSet -> ioR.map(r -> {
                Set<R> newSet = new LinkedHashSet<>(resSet);
                newSet.add(r);
                return newSet;
            }));
        }
        return acc.map(Collections::unmodifiableSet);
    }

    public static <T> IO<Set<T>> sequenceIO(Set<? extends IO<T>> set) {
        return traverseIO(set, Function.identity());
    }

    public static <T, L, R> Either<L, Set<R>> traverseEither(Set<T> set, Function<? super T, ? extends Either<L, R>> fn) {
        Objects.requireNonNull(set, "set must not be null");
        Objects.requireNonNull(fn, "fn must not be null");
        Set<R> result = new LinkedHashSet<>(set.size());
        for (T item : set) {
            Either<L, R> either = fn.apply(item);
            if (either.isLeft()) {
                return Either.left(either.getLeft());
            }
            result.add(either.getRight());
        }
        return Either.right(Collections.unmodifiableSet(result));
    }

    public static <L, T> Either<L, Set<T>> sequenceEither(Set<? extends Either<L, T>> set) {
        return traverseEither(set, Function.identity());
    }
}
