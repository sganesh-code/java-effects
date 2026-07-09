package io.effects.core;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A typeclass that extends Monad to represent structured error propagation and recovery.
 *
 * @param <T> the success type
 * @param <E> the error type
 */
public interface MonadError<T, E> extends Monad<T> {

    /**
     * Lift an error of type E into the monadic error context.
     */
    F<T, ? extends Functor<T>> raiseError(E error);

    /**
     * Recover from a monadic failure by switching to a new monadic computation.
     */
    F<T, ? extends Functor<T>> handleErrorWith(
        F<T, ? extends Functor<T>> fa, 
        Function<? super E, ? extends F<T, ? extends Functor<T>>> fn
    );

    /**
     * Asserts a predicate on a monadic value. If the predicate fails, raises the given error E.
     */
    @SuppressWarnings("unchecked")
    default F<T, ? extends Functor<T>> ensure(
        F<T, ? extends Functor<T>> fa, 
        Predicate<? super T> pred, 
        E error
    ) {
        var monad = (Monad<T>) (Object) fa;
        return monad.flatMap(t -> pred.test(t) 
            ? (F<T, ? extends Functor<T>>) (Object) monad.pure(t) 
            : raiseError(error)
        );
    }
}
