package io.effects.core;

import io.effects.core.F;
import io.effects.core.Functor;
import io.effects.core.MonadError;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A monadic IO representation that describes a computation.
 * Evaluation is lazy and referentially transparent.
 *
 * @param <A> the type of the value produced by this IO
 */
public sealed interface IO<A> extends MonadError<A, Throwable> permits IO.Pure, IO.Delay, IO.FlatMap, IO.Async, IO.Error, IO.HandleError {

    // Nested register for parallel tuple results
    record Pair<A, B>(A first, B second) {}

    // Typeclass Core Overrides

    @Override
    default <R> IO<R> pure(R value) {
        return IO.of(value);
    }

    /**
     * Map a function over the result of this IO.
     */
    @Override
    default <B> IO<B> map(Function<? super A, ? extends B> f) {
        Objects.requireNonNull(f);
        return flatMap(a -> pure(f.apply(a)));
    }

    /**
     * Compose another IO computation sequentially.
     */
    @SuppressWarnings("unchecked")
    @Override
    default <B> IO<B> flatMap(Function<? super A, ? extends F<B, ? extends Functor<B>>> f) {
        Objects.requireNonNull(f);
        return new FlatMap<>(this, (Function<? super A, ? extends IO<B>>) f);
    }

    // MonadError Core Overrides
    @Override
    default IO<A> raiseError(Throwable error) {
        return IO.failed(error);
    }

    @SuppressWarnings("unchecked")
    @Override
    default IO<A> handleErrorWith(
        F<A, ? extends Functor<A>> fa, 
        Function<? super Throwable, ? extends F<A, ? extends Functor<A>>> fn
    ) {
        var io = (IO<A>) fa;
        return io.handleErrorWith((Function<Throwable, ? extends IO<A>>) fn);
    }

    // Covariant Typeclass Helper Overrides

    @SuppressWarnings("unchecked")
    @Override
    default <R> IO<R> as(R value) {
        return (IO<R>) MonadError.super.as(value);
    }

    @SuppressWarnings("unchecked")
    @Override
    default IO<Void> voided() {
        return (IO<Void>) MonadError.super.voided();
    }

    @SuppressWarnings("unchecked")
    @Override
    default <B, R> IO<R> map2(
        F<B, ? extends Functor<B>> fb, 
        java.util.function.BiFunction<? super A, ? super B, ? extends R> fn
    ) {
        return (IO<R>) MonadError.super.map2(fb, fn);
    }

    @SuppressWarnings("unchecked")
    @Override
    default <B> IO<A> productL(F<B, ? extends Functor<B>> fb) {
        return (IO<A>) MonadError.super.productL(fb);
    }

    @SuppressWarnings("unchecked")
    @Override
    default <B> IO<B> productR(F<B, ? extends Functor<B>> fb) {
        return (IO<B>) MonadError.super.productR(fb);
    }

    @SuppressWarnings("unchecked")
    @Override
    default <R> IO<R> flatten() {
        return (IO<R>) MonadError.super.flatten();
    }

    @SuppressWarnings("unchecked")
    @Override
    default <R> IO<R> andThen(F<R, ? extends Functor<R>> next) {
        return (IO<R>) MonadError.super.andThen(next);
    }

    @SuppressWarnings("unchecked")
    @Override
    default <R> IO<R> ifM(
        F<Boolean, ? extends Functor<Boolean>> cond, 
        F<R, ? extends Functor<R>> thn, 
        F<R, ? extends Functor<R>> els
    ) {
        return (IO<R>) MonadError.super.ifM(cond, thn, els);
    }

    @SuppressWarnings("unchecked")
    @Override
    default IO<A> ensure(
        F<A, ? extends Functor<A>> fa, 
        java.util.function.Predicate<? super A> pred, 
        Throwable error
    ) {
        return (IO<A>) MonadError.super.ensure(fa, pred, error);
    }

    // Direct IO Combinators

    /**
     * Asserts a predicate on this IO's success value. If the predicate fails, raises the given error.
     */
    default IO<A> ensure(java.util.function.Predicate<? super A> pred, Throwable error) {
        return ensure(this, pred, error);
    }

    /**
     * Materializes any failures in the error channel into an Either.
     */
    default IO<Either<Throwable, A>> attempt() {
        return this.<Either<Throwable, A>>map(Either::right)
                .handleErrorWith(t -> pure(Either.left(t)));
    }

    /**
     * Recover from any errors by switching to another IO computation.
     */
    default IO<A> handleErrorWith(Function<Throwable, ? extends IO<A>> f) {
        Objects.requireNonNull(f);
        return new HandleError<>(this, f);
    }

    // Execution entry points

    /**
     * Executes this IO asynchronously, invoking the callback with the result.
     */
    default void unsafeRunAsync(Consumer<Either<Throwable, A>> cb) {
        Objects.requireNonNull(cb);
        IORuntime.global().runAsync(this, cb);
    }

    /**
     * Executes this IO synchronously on the calling thread, blocking until completion.
     */
    default A unsafeRunSync() {
        return IORuntime.global().runSync(this);
    }

    // Static Factory Methods

    /**
     * Lift a pure, pre-computed value into an IO.
     */
    static <A> IO<A> of(A value) {
        return new Pure<>(value);
    }

    /**
     * Creates a Kleisli arrow wrapping an IO function.
     */
    static <A, B> io.effects.core.Kleisli<A, B> kleisli(Function<? super A, ? extends IO<B>> run) {
        Objects.requireNonNull(run);
        return new io.effects.core.Kleisli<>(run);
    }

    /**
     * Suspend a synchronous, side-effecting computation.
     */
    static <A> IO<A> delay(Supplier<A> thunk) {
        Objects.requireNonNull(thunk);
        return new Delay<>(thunk);
    }

    /**
     * Create an asynchronous IO computation.
     * The register callback is called with a callback that should be invoked with the result when ready.
     */
    static <A> IO<A> async(Consumer<Consumer<Either<Throwable, A>>> register) {
        Objects.requireNonNull(register);
        return new Async<>(register);
    }

    /**
     * Create an IO that immediately fails with the given error.
     */
    static <A> IO<A> failed(Throwable error) {
        Objects.requireNonNull(error);
        return new Error<>(error);
    }

    /**
     * Shift evaluation onto the virtual thread pool asynchronous boundary.
     */
    static IO<Void> shift() {
        return IORuntime.shift();
    }

    // Structured Concurrency Primitives

    /**
     * Runs two computations concurrently. The winner's result is returned.
     */
    static <A, B> IO<Either<A, B>> race(IO<A> ioA, IO<B> ioB) {
        Objects.requireNonNull(ioA);
        Objects.requireNonNull(ioB);
        return IO.async(cb -> {
            var completed = new java.util.concurrent.atomic.AtomicBoolean(false);

            ioA.unsafeRunAsync(resA -> {
                if (completed.compareAndSet(false, true)) {
                    resA.fold(
                        err -> { cb.accept(Either.left(err)); return null; },
                        val -> { cb.accept(Either.right(Either.left(val))); return null; }
                    );
                }
            });

            ioB.unsafeRunAsync(resB -> {
                if (completed.compareAndSet(false, true)) {
                    resB.fold(
                        err -> { cb.accept(Either.left(err)); return null; },
                        val -> { cb.accept(Either.right(Either.right(val))); return null; }
                    );
                }
            });
        });
    }

    /**
     * Runs two computations concurrently, combining their results via a BiFunction.
     * Fails fast if either computation fails.
     */
    static <A, B, C> IO<C> parMap2(IO<A> ioA, IO<B> ioB, java.util.function.BiFunction<? super A, ? super B, ? extends C> f) {
        Objects.requireNonNull(ioA);
        Objects.requireNonNull(ioB);
        Objects.requireNonNull(f);
        return IO.async(cb -> {
            var resultA = new java.util.concurrent.atomic.AtomicReference<Either<Throwable, A>>();
            var resultB = new java.util.concurrent.atomic.AtomicReference<Either<Throwable, B>>();
            var completed = new java.util.concurrent.atomic.AtomicBoolean(false);

            java.lang.Runnable checkAndComplete = () -> {
                var rA = resultA.get();
                var rB = resultB.get();
                if (rA != null && rB != null && completed.compareAndSet(false, true)) {
                        rA.fold(
                            errA -> { cb.accept(Either.left(errA)); return null; },
                            valA -> rB.fold(
                                errB -> { cb.accept(Either.left(errB)); return null; },
                                valB -> { cb.accept(Either.right(f.apply(valA, valB))); return null; }
                            )
                        );
                    }

            };

            ioA.unsafeRunAsync(resA -> resA.fold(
                err -> {
                    if (completed.compareAndSet(false, true)) {
                        cb.accept(Either.left(err));
                    }
                    return null;
                },
                val -> {
                    resultA.set(Either.right(val));
                    checkAndComplete.run();
                    return null;
                }
            ));

            ioB.unsafeRunAsync(resB -> resB.fold(
                err -> {
                    if (completed.compareAndSet(false, true)) {
                        cb.accept(Either.left(err));
                    }
                    return null;
                },
                val -> {
                    resultB.set(Either.right(val));
                    checkAndComplete.run();
                    return null;
                }
            ));
        });
    }

    /**
     * Runs two computations concurrently, returning their results combined as a Pair.
     * Fails fast if either computation fails.
     */
    static <A, B> IO<Pair<A, B>> parTuple(IO<A> ioA, IO<B> ioB) {
        return parMap2(ioA, ioB, Pair::new);
    }

    // Subtypes / ADT Nodes

    record Pure<A>(A value) implements IO<A> {}

    record Delay<A>(Supplier<A> thunk) implements IO<A> {}

    record FlatMap<X, A>(IO<X> source, Function<? super X, ? extends IO<A>> f) implements IO<A> {}

    record Async<A>(Consumer<Consumer<Either<Throwable, A>>> register) implements IO<A> {}

    record Error<A>(Throwable error) implements IO<A> {}

    record HandleError<A>(IO<A> source, Function<Throwable, ? extends IO<A>> handler) implements IO<A> {}
}
