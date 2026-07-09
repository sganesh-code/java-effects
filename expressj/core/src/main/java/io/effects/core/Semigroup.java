package io.effects.core;

/**
 * An algebraic structure representing binary combination.
 *
 * @param <A> the type of elements being combined
 */
public interface Semigroup<A> {

    /**
     * Combines two elements of type A.
     */
    A combine(A x, A y);
}
