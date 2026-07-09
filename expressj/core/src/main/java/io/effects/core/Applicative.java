package io.effects.core;

import java.util.function.Function;

public interface Applicative<T> extends Functor<T> {

    @SuppressWarnings("unchecked")
    @Override
    default <R> F<R, ? extends Functor<R>> map(Function<? super T, ? extends R> fn) {
        return ap((F<Function<? super T, ? extends R>, ? extends Functor<Function<? super T, ? extends R>>>) (Object) pure(fn));
    }

    <R> F<R, ? extends Functor<R>> pure(R value);
    <R> F<R, ? extends Functor<R>> ap(F<Function<? super T, ? extends R>, ? extends Functor<Function<? super T, ? extends R>>> fn);

    @SuppressWarnings("unchecked")
    default <B, R> F<R, ? extends Functor<R>> map2(
        F<B, ? extends Functor<B>> fb, 
        java.util.function.BiFunction<? super T, ? super B, ? extends R> fn
    ) {
        var appB = (Applicative<B>) (Object) fb;
        var appFn = (F<Function<? super B, ? extends R>, ? extends Functor<Function<? super B, ? extends R>>>) (Object)
            this.map(t -> (Function<? super B, ? extends R>) b -> fn.apply(t, b));
        return appB.ap((F<Function<? super B, ? extends R>, ? extends Functor<Function<? super B, ? extends R>>>) (Object) appFn);
    }

    default <B> F<T, ? extends Functor<T>> productL(F<B, ? extends Functor<B>> fb) {
        return map2(fb, (t, b) -> t);
    }

    default <B> F<B, ? extends Functor<B>> productR(F<B, ? extends Functor<B>> fb) {
        return map2(fb, (t, b) -> b);
    }

    /**
     * Conditionally runs an Applicative effect based on a boolean flag.
     */
    @SuppressWarnings("unchecked")
    default F<Void, ? extends Functor<Void>> when(boolean cond, F<Void, ? extends Functor<Void>> action) {
        return cond ? action : (F<Void, ? extends Functor<Void>>) (Object) pure(null);
    }
}
