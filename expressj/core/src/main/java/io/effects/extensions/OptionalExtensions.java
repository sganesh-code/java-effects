package io.effects.extensions;

import io.effects.Either;
import io.effects.IO;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Functional and monadic extension methods for {@link java.util.Optional}.
 * Designed for use with Lombok's {@code @ExtensionMethod}.
 * Implements the Pragmatic Hybrid Approach: ergonomic functors combined with effect-bridging.
 */
public final class OptionalExtensions {

    private OptionalExtensions() {}

    // Ergonomic Functor Operations

    public static <T, R> Optional<R> as(Optional<T> optional, R value) {
        Objects.requireNonNull(optional, "optional must not be null");
        return optional.map(t -> value);
    }

    public static <T> Optional<Void> voided(Optional<T> optional) {
        Objects.requireNonNull(optional, "optional must not be null");
        return optional.map(t -> null);
    }

    // Effect Sequence & Traversal Operations

    public static <T, R> IO<Optional<R>> traverseIO(Optional<T> optional, Function<? super T, ? extends IO<R>> fn) {
        Objects.requireNonNull(optional, "optional must not be null");
        Objects.requireNonNull(fn, "fn must not be null");
        if (optional.isEmpty()) {
            return IO.of(Optional.empty());
        }
        return fn.apply(optional.get()).map(Optional::ofNullable);
    }

    public static <T> IO<Optional<T>> sequenceIO(Optional<? extends IO<T>> optional) {
        return traverseIO(optional, Function.identity());
    }

    public static <T, L, R> Either<L, Optional<R>> traverseEither(Optional<T> optional, Function<? super T, ? extends Either<L, R>> fn) {
        Objects.requireNonNull(optional, "optional must not be null");
        Objects.requireNonNull(fn, "fn must not be null");
        if (optional.isEmpty()) {
            return Either.right(Optional.empty());
        }
        Either<L, R> either = fn.apply(optional.get());
        if (either.isLeft()) {
            return Either.left(either.getLeft());
        }
        return Either.right(Optional.ofNullable(either.getRight()));
    }

    public static <L, T> Either<L, Optional<T>> sequenceEither(Optional<? extends Either<L, T>> optional) {
        return traverseEither(optional, Function.identity());
    }
}
