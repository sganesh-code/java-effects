package io.effects.recipes;

import io.effects.IO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * An Object-Oriented "Recipe" representing a Saga transaction.
 * A Saga is a sequence of steps where each step has an action and a compensating action (rollback).
 * If any step fails, the compensating actions of all successfully completed steps are run in reverse order.
 *
 * @param <A> the return type of the Saga
 */
public final class Saga<A> {
    private final List<Step> steps;
    private final Function<Object, IO<A>> finalMap;

    private Saga(List<Step> steps, Function<Object, IO<A>> finalMap) {
        this.steps = steps;
        this.finalMap = finalMap;
    }

    /**
     * Initializes a new, empty Saga transaction.
     */
    public static Saga<Void> create() {
        return new Saga<>(new ArrayList<>(), x -> IO.of(null));
    }

    /**
     * Adds a step to the Saga with an action and a corresponding compensating action.
     *
     * @param action The forward action that produces a value of type T, wrapped in IO.
     * @param compensate A function that takes the produced value and returns an IO representing the rollback.
     * @param <T> the type of the value produced by the step
     * @return a new Saga instance with this step appended
     */
    @SuppressWarnings("unchecked")
    public <T> Saga<T> addStep(IO<T> action, Function<T, IO<Void>> compensate) {
        Objects.requireNonNull(action);
        Objects.requireNonNull(compensate);
        List<Step> newSteps = new ArrayList<>(this.steps);
        newSteps.add(new Step(action, (Function<Object, IO<Void>>) (Function<?, ?>) compensate));
        return new Saga<>(newSteps, x -> IO.of((T) x));
    }

    /**
     * Maps the final result of the Saga.
     */
    public <B> Saga<B> map(Function<? super A, ? extends B> f) {
        Objects.requireNonNull(f);
        return new Saga<>(this.steps, x -> finalMap.apply(x).map(f));
    }

    /**
     * Sequentially composes the final result of the Saga to another IO.
     */
    public <B> Saga<B> flatMap(Function<? super A, ? extends IO<B>> f) {
        Objects.requireNonNull(f);
        return new Saga<>(this.steps, x -> finalMap.apply(x).flatMap(f));
    }

    /**
     * Compiles this Saga into a single, transactionally safe, lazy IO computation.
     */
    @SuppressWarnings("unchecked")
    public IO<A> transact() {
        return runSteps(0, new ArrayList<>()).flatMap(finalMap);
    }

    @SuppressWarnings("unchecked")
    private IO<Object> runSteps(int index, List<IO<Void>> rollbacks) {
        if (index >= steps.size()) {
            return IO.of(null);
        }

        Step step = steps.get(index);
        return ((IO<Object>) step.action()).handleErrorWith(t -> {
            // Trigger all previously executed rollbacks in reverse order
            IO<Void> compensationChain = IO.of(null);
            List<IO<Void>> reversedRollbacks = new ArrayList<>(rollbacks);
            Collections.reverse(reversedRollbacks);
            for (IO<Void> rb : reversedRollbacks) {
                compensationChain = compensationChain.flatMap(v -> rb);
            }
            return compensationChain.flatMap(v -> IO.failed(t));
        }).flatMap(result -> {
            IO<Void> rollback = step.compensate().apply(result);
            List<IO<Void>> nextRollbacks = new ArrayList<>(rollbacks);
            nextRollbacks.add(rollback);

            if (index == steps.size() - 1) {
                return IO.of(result);
            }

            return runSteps(index + 1, nextRollbacks);
        });
    }

    private record Step(IO<?> action, Function<Object, IO<Void>> compensate) {}
}
