package io.effects.optics;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A Prism represents a focus on a specific variant of a sum type S.
 *
 * @param <S> the parent sum type (e.g. Either, Sealed Interface)
 * @param <A> the specific focused sub-variant of the sum type
 */
public interface Prism<S, A> {

    /**
     * Focuses on the sub-variant if S matches it, otherwise returns empty.
     */
    Optional<A> getOption(S source);

    /**
     * Lifts a value of the sub-variant into the parent sum type context.
     */
    S reverseGet(A value);

    /**
     * Modifies the sub-variant inside S if it exists, otherwise leaves S unchanged.
     */
    default S modify(S source, Function<A, A> f) {
        Objects.requireNonNull(f);
        return getOption(source)
            .map(a -> reverseGet(f.apply(a)))
            .orElse(source);
    }

    /**
     * Composes this Prism with another Prism to focus deeper into nested sum type structures.
     */
    default <B> Prism<S, B> compose(Prism<A, B> next) {
        Objects.requireNonNull(next);
        return new Prism<>() {
            @Override
            public Optional<B> getOption(S source) {
                return Prism.this.getOption(source).flatMap(next::getOption);
            }

            @Override
            public S reverseGet(B value) {
                return Prism.this.reverseGet(next.reverseGet(value));
            }
        };
    }
}
