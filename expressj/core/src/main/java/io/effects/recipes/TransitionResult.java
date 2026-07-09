package io.effects.recipes;

/**
 * A generalized, type-safe representation of a domain state transition result.
 * It couples the mutated Aggregate Root (A) alongside the immutable chronological Domain Event (E) produced.
 */
public record TransitionResult<A, E>(A aggregate, E event) {
}
