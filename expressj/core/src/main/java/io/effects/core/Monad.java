package io.effects.core;

import java.util.function.Function;

public interface Monad <T> extends Applicative<T> {

    @Override
    <R> F<R, ? extends Functor<R>> pure(R value);

    @SuppressWarnings("unchecked")
    @Override
    default <R> F<R, ? extends Functor<R>> ap(F<Function<? super T, ? extends R>, ? extends Functor<Function<? super T, ? extends R>>> fn) {
        var monadF = (Monad<Function<? super T, ? extends R>>) (Object) fn;
        return monadF.flatMap(f -> (F<R, ? extends Functor<R>>) (Object) this.map(f));
    }

    @Override
    default <R> F<R, ? extends Functor<R>> map(Function<? super T, ? extends R> fn) {
        return flatMap(t -> pure(fn.apply(t)));
    }

    <R> F<R, ? extends Functor<R>> flatMap(Function<? super T, ? extends F<R, ? extends Functor<R>>> fn);

    @SuppressWarnings("unchecked")
    default <R> F<R, ? extends Functor<R>> flatten() {
        return flatMap(t -> (F<R, ? extends Functor<R>>) t);
    }

    default <R> F<R, ? extends Functor<R>> andThen(F<R, ? extends Functor<R>> next) {
        return flatMap(t -> next);
    }

    /**
     * Monadic conditional branching. Flattens a boolean effect to choose between two computations.
     */
    @SuppressWarnings("unchecked")
    default <R> F<R, ? extends Functor<R>> ifM(
        F<Boolean, ? extends Functor<Boolean>> cond, 
        F<R, ? extends Functor<R>> thn, 
        F<R, ? extends Functor<R>> els
    ) {
        var condMonad = (Monad<Boolean>) (Object) cond;
        return condMonad.flatMap(c -> c 
            ? (F<R, ? extends Functor<R>>) (Object) thn 
            : (F<R, ? extends Functor<R>>) (Object) els
        );
    }
}
