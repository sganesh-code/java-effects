package io.effects.core;

import java.util.function.Function;

/**
 * A typeclass representing a container with two independent mapping channels.
 * Useful for dual-channel containers like Either or Pair.
 *
 * @param <T> the type of the first channel
 * @param <U> the type of the second channel
 */
public interface Bifunctor<T, U> extends F<U, Functor<U>> {

    /**
     * Simultaneously maps functions over both channels.
     */
    <R, S> F<S, ? extends Functor<S>> bimap(Function<? super T, ? extends R> f, Function<? super U, ? extends S> g);

    /**
     * Maps a function over the first channel only.
     */
    @SuppressWarnings("unchecked")
    default <R> F<U, ? extends Functor<U>> mapFirst(Function<? super T, ? extends R> f) {
        return bimap(f, Function.identity());
    }

    /**
     * Maps a function over the second channel only.
     */
    default <S> F<S, ? extends Functor<S>> mapSecond(Function<? super U, ? extends S> g) {
        return bimap(Function.identity(), g);
    }
}
