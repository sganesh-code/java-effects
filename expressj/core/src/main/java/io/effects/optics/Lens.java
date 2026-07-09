package io.effects.optics;

import java.util.Objects;
import java.util.function.Function;

/**
 * A Lens represents a purely functional getter and setter for a field of a product type S.
 *
 * @param <S> the parent/source product structure
 * @param <A> the child field focused by this lens
 */
public interface Lens<S, A> {

    /**
     * Gets the child field A from the parent structure S.
     */
    A get(S source);

    /**
     * Immutable set: takes a parent S and returns a cloned/updated S with the child field replaced.
     */
    S set(S source, A value);

    /**
     * Modifies the child field of a parent structure purely.
     */
    default S modify(S source, Function<A, A> f) {
        Objects.requireNonNull(f);
        return set(source, f.apply(get(source)));
    }

    /**
     * Composes this Lens with another Lens to focus deeper into nested product structures.
     */
    default <B> Lens<S, B> compose(Lens<A, B> next) {
        Objects.requireNonNull(next);
        return new Lens<>() {
            @Override
            public B get(S source) {
                return next.get(Lens.this.get(source));
            }

            @Override
            public S set(S source, B value) {
                return Lens.this.set(source, next.set(Lens.this.get(source), value));
            }
        };
    }
}
