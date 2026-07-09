package io.effects.optics;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * An Iso represents a lossless, 1-to-1 bidirectional equivalence between S and A.
 * It is both a Lens and a Prism.
 *
 * @param <S> the first type
 * @param <A> the second type
 */
public interface Iso<S, A> extends Lens<S, A>, Prism<S, A> {

    @Override
    A get(S source);

    @Override
    S reverseGet(A value);

    @Override
    default S set(S source, A value) {
        return reverseGet(value);
    }

    @Override
    default Optional<A> getOption(S source) {
        return Optional.of(get(source));
    }

    /**
     * Resolves Java's multiple-inheritance default method conflict between Lens and Prism.
     */
    @Override
    default S modify(S source, Function<A, A> f) {
        return Lens.super.modify(source, f);
    }

    /**
     * Composes this Iso with another Iso to build sequential conversions.
     */
    default <B> Iso<S, B> compose(Iso<A, B> next) {
        Objects.requireNonNull(next);
        return new Iso<>() {
            @Override
            public B get(S source) {
                return next.get(Iso.this.get(source));
            }

            @Override
            public S reverseGet(B value) {
                return Iso.this.reverseGet(next.reverseGet(value));
            }

            @Override
            public S modify(S s, Function<B, B> f) {
                return Iso.super.modify(s, f);
            }
        };
    }
}
