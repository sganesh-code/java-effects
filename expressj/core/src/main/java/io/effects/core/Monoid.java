package io.effects.core;

/**
 * An algebraic structure representing binary combination with an identity element.
 *
 * @param <A> the type of elements
 */
public interface Monoid<A> extends Semigroup<A> {

    /**
     * The identity element of the Monoid, such that combine(x, empty) == x.
     */
    A empty();
}
