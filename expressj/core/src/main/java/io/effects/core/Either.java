package io.effects.core;

import io.effects.core.Bifunctor;
import io.effects.core.F;
import io.effects.core.Functor;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

/**
 * A sum type representing a value of one of two possible types.
 * An instance of Either is either an instance of Left or Right.
 * By convention, Left is used for failure/error and Right is used for success.
 *
 * @param <L> the Left type
 * @param <R> the Right type
 */
public sealed interface Either<L, R> extends Bifunctor<L, R> permits Either.Left, Either.Right {

    boolean isLeft();
    boolean isRight();

    L getLeft();
    R getRight();

    // Bifunctor Core Overrides

    @SuppressWarnings("unchecked")
    @Override
    default <R1, S> Either<R1, S> bimap(Function<? super L, ? extends R1> f, Function<? super R, ? extends S> g) {
        Objects.requireNonNull(f);
        Objects.requireNonNull(g);
        if (this instanceof Left(L value)) {
            return Either.left(f.apply(value));
        } else if (this instanceof Right(R value)) {
            return Either.right(g.apply(value));
        }
        throw new IllegalStateException("Unknown Either subtype: " + this);
    }

    @SuppressWarnings("unchecked")
    @Override
    default <R1> Either<R1, R> mapFirst(Function<? super L, ? extends R1> f) {
        return (Either<R1, R>) Bifunctor.super.mapFirst(f);
    }

    @SuppressWarnings("unchecked")
    @Override
    default <S> Either<L, S> mapSecond(Function<? super R, ? extends S> g) {
        return (Either<L, S>) Bifunctor.super.mapSecond(g);
    }

    // Monadic Either Operations

    @SuppressWarnings("unchecked")
    default <B> Either<L, B> map(Function<? super R, ? extends B> f) {
        Objects.requireNonNull(f);
        if (this instanceof Right(R value)) {
            return Either.right(f.apply(value));
        } else {
            return (Either<L, B>) this;
        }
    }

    @SuppressWarnings("unchecked")
    default <B> Either<L, B> flatMap(Function<? super R, ? extends Either<L, B>> f) {
        Objects.requireNonNull(f);
        if (this instanceof Right(R value)) {
            return f.apply(value);
        } else {
            return (Either<L, B>) this;
        }
    }

    default <C> C fold(Function<? super L, ? extends C> ifLeft, Function<? super R, ? extends C> ifRight) {
        Objects.requireNonNull(ifLeft);
        Objects.requireNonNull(ifRight);
        if (this instanceof Left(L value)) {
            return ifLeft.apply(value);
        } else if (this instanceof Right(R value)) {
            return ifRight.apply(value);
        }
        throw new IllegalStateException("Unknown Either subtype: " + this);
    }

    static <L, R> Either<L, R> left(L value) {
        return new Left<>(value);
    }

    static <L, R> Either<L, R> right(R value) {
        return new Right<>(value);
    }

    // Advanced Static Recipes

    /**
     * Combines two independent Either instances, accumulating errors using a Semigroup on failure.
     */
    @SuppressWarnings("unchecked")
    static <E, A, B, R> Either<E, R> validateParallel(
        Either<E, A> targetA, 
        Either<E, B> targetB, 
        io.effects.core.Semigroup<E> semigroup,
        java.util.function.BiFunction<? super A, ? super B, ? extends R> combiner
    ) {
        Objects.requireNonNull(targetA);
        Objects.requireNonNull(targetB);
        Objects.requireNonNull(semigroup);
        Objects.requireNonNull(combiner);

        if (targetA.isLeft() && targetB.isLeft()) {
            return Either.left(semigroup.combine(targetA.getLeft(), targetB.getLeft()));
        } else if (targetA.isLeft()) {
            return Either.left(targetA.getLeft());
        } else if (targetB.isLeft()) {
            return Either.left(targetB.getLeft());
        } else {
            return Either.right(combiner.apply(targetA.getRight(), targetB.getRight()));
        }
    }

    /**
     * A Prism focusing on the Left (failure) channel of an Either.
     */
    static <L, R> io.effects.optics.Prism<Either<L, R>, L> leftPrism() {
        return new io.effects.optics.Prism<>() {
            @Override
            public java.util.Optional<L> getOption(Either<L, R> source) {
                return source.isLeft() ? java.util.Optional.of(source.getLeft()) : java.util.Optional.empty();
            }

            @Override
            public Either<L, R> reverseGet(L value) {
                return Either.left(value);
            }
        };
    }

    /**
     * A Prism focusing on the Right (success) channel of an Either.
     */
    static <L, R> io.effects.optics.Prism<Either<L, R>, R> rightPrism() {
        return new io.effects.optics.Prism<>() {
            @Override
            public java.util.Optional<R> getOption(Either<L, R> source) {
                return source.isRight() ? java.util.Optional.of(source.getRight()) : java.util.Optional.empty();
            }

            @Override
            public Either<L, R> reverseGet(R value) {
                return Either.right(value);
            }
        };
    }

    record Left<L, R>(L value) implements Either<L, R> {
        @Override public boolean isLeft() { return true; }
        @Override public boolean isRight() { return false; }
        @Override public L getLeft() { return value; }
        @Override public R getRight() { throw new NoSuchElementException("getRight on Left"); }
    }

    record Right<L, R>(R value) implements Either<L, R> {
        @Override public boolean isLeft() { return false; }
        @Override public boolean isRight() { return true; }
        @Override public L getLeft() { throw new NoSuchElementException("getLeft on Right"); }
        @Override public R getRight() { return value; }
    }
}
