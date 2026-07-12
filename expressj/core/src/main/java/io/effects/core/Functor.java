package io.effects.core;

import java.util.function.Function;

public interface Functor<T> extends F<T, Functor<T>> {
    <R> F<R, ? extends Functor<R>> map(Function<? super T, ? extends R> fn);

    default <R> F<R, ? extends Functor<R>> as(R value) {
        return map(t -> value);
    }

    default F<Void, ? extends Functor<Void>> voided() {
        return map(t -> null);
    }

    /**
     * Lifts a function from A -> B into a Functor-compatible function {@code F<A> -> F<B>}.
     */
    @SuppressWarnings("unchecked")
    default <R> Function<F<T, ? extends Functor<T>>, F<R, ? extends Functor<R>>> lift(
        Function<? super T, ? extends R> fn
    ) {
        return fa -> {
            var functor = (Functor<T>) fa;
            return functor.map(fn);
        };
    }
}
