package io.effects.extensions;

import io.effects.Either;
import io.effects.IO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Functional and monadic extension methods for {@link java.util.List}.
 * Designed for use with Lombok's {@code @ExtensionMethod}.
 * Implements the Pragmatic Hybrid Approach: ergonomic functors and filters combined with effect-bridging.
 */
public final class ListExtensions {

    private ListExtensions() {}

    // Ergonomic Functor & Filter Operations

    public static <T, R> List<R> map(List<T> list, Function<? super T, ? extends R> fn) {
        Objects.requireNonNull(list, "list must not be null");
        Objects.requireNonNull(fn, "fn must not be null");
        List<R> result = new ArrayList<>(list.size());
        for (T item : list) {
            result.add(fn.apply(item));
        }
        return Collections.unmodifiableList(result);
    }

    public static <T, R> List<R> as(List<T> list, R value) {
        return map(list, t -> value);
    }

    public static <T> List<Void> voided(List<T> list) {
        return map(list, t -> null);
    }

    public static <T> List<T> filter(List<T> list, java.util.function.Predicate<? super T> predicate) {
        Objects.requireNonNull(list, "list must not be null");
        Objects.requireNonNull(predicate, "predicate must not be null");
        List<T> result = new ArrayList<>();
        for (T item : list) {
            if (predicate.test(item)) {
                result.add(item);
            }
        }
        return Collections.unmodifiableList(result);
    }

    // Foldable Operations

    public static <T, B> B foldLeft(List<T> list, B zero, java.util.function.BiFunction<B, ? super T, B> f) {
        Objects.requireNonNull(list, "list must not be null");
        Objects.requireNonNull(f, "f must not be null");
        B acc = zero;
        for (T item : list) {
            acc = f.apply(acc, item);
        }
        return acc;
    }

    public static <T, B> B foldRight(List<T> list, B zero, java.util.function.BiFunction<? super T, B, B> f) {
        Objects.requireNonNull(list, "list must not be null");
        Objects.requireNonNull(f, "f must not be null");
        B acc = zero;
        for (int i = list.size() - 1; i >= 0; i--) {
            acc = f.apply(list.get(i), acc);
        }
        return acc;
    }

    // Effect Sequence & Traversal Operations

    public static <T, R> IO<List<R>> traverseIO(List<T> list, Function<? super T, ? extends IO<R>> fn) {
        Objects.requireNonNull(list, "list must not be null");
        Objects.requireNonNull(fn, "fn must not be null");
        IO<List<R>> acc = IO.of(new ArrayList<>());
        for (T item : list) {
            IO<R> ioR = fn.apply(item);
            acc = acc.flatMap(resList -> ioR.map(r -> {
                List<R> newList = new ArrayList<>(resList);
                newList.add(r);
                return newList;
            }));
        }
        return acc.map(Collections::unmodifiableList);
    }

    public static <T> IO<List<T>> sequenceIO(List<? extends IO<T>> list) {
        return traverseIO(list, Function.identity());
    }

    public static <T, L, R> Either<L, List<R>> traverseEither(List<T> list, Function<? super T, ? extends Either<L, R>> fn) {
        Objects.requireNonNull(list, "list must not be null");
        Objects.requireNonNull(fn, "fn must not be null");
        List<R> result = new ArrayList<>(list.size());
        for (T item : list) {
            Either<L, R> either = fn.apply(item);
            if (either.isLeft()) {
                return Either.left(either.getLeft());
            }
            result.add(either.getRight());
        }
        return Either.right(Collections.unmodifiableList(result));
    }

    public static <L, T> Either<L, List<T>> sequenceEither(List<? extends Either<L, T>> list) {
        return traverseEither(list, Function.identity());
    }

    // Structural Helpers

    public static <A, B> List<IO.Pair<A, B>> zip(List<A> listA, List<B> listB) {
        Objects.requireNonNull(listA, "listA must not be null");
        Objects.requireNonNull(listB, "listB must not be null");
        int size = Math.min(listA.size(), listB.size());
        List<IO.Pair<A, B>> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new IO.Pair<>(listA.get(i), listB.get(i)));
        }
        return Collections.unmodifiableList(result);
    }
}
