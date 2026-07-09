package io.effects.core;

import java.util.Objects;
import java.util.function.Function;

/**
 * A Kleisli arrow represents a function from a plain type A to a monadic effect F<B>.
 * Enables sequential composition of effectful functions (monadic pipelines).
 *
 * @param <A> the input type
 * @param <B> the output value type inside the monadic effect
 */
public final class Kleisli<A, B> {
    private final Function<? super A, ? extends F<B, ? extends Functor<B>>> run;

    public Kleisli(Function<? super A, ? extends F<B, ? extends Functor<B>>> run) {
        this.run = Objects.requireNonNull(run);
    }

    /**
     * Executes this Kleisli arrow on an input.
     */
    public F<B, ? extends Functor<B>> run(A input) {
        return run.apply(input);
    }

    /**
     * Sequentially composes this Kleisli arrow with another Kleisli arrow.
     * Corresponds to the monadic composition operator (Haskell >=>).
     */
    @SuppressWarnings("unchecked")
    public <C> Kleisli<A, C> andThen(Kleisli<B, C> next) {
        Objects.requireNonNull(next);
        return new Kleisli<>(a -> {
            var fb = run.apply(a);
            var monad = (Monad<B>) (Object) fb;
            return monad.flatMap(b -> next.run(b));
        });
    }

    /**
     * Composes another Kleisli arrow before this one.
     */
    @SuppressWarnings("unchecked")
    public <C> Kleisli<C, B> compose(Kleisli<C, A> before) {
        Objects.requireNonNull(before);
        return new Kleisli<>(c -> {
            var fa = before.run(c);
            var monad = (Monad<A>) (Object) fa;
            return monad.flatMap(a -> run.apply(a));
        });
    }
}
