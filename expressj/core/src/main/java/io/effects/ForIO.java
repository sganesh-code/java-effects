package io.effects;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A type-safe monadic For-comprehension facade for IO computations.
 * Allows flatly chaining up to 6 sequential IO steps, avoiding nested flatMaps.
 */
public final class ForIO {
    private ForIO() {}

    @FunctionalInterface
    public interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    @FunctionalInterface
    public interface QuadFunction<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }

    @FunctionalInterface
    public interface PentaFunction<A, B, C, D, E, R> {
        R apply(A a, B b, C c, D d, E e);
    }

    @FunctionalInterface
    public interface HexaFunction<A, B, C, D, E, F, R> {
        R apply(A a, B b, C c, D d, E e, F f);
    }

    /**
     * Start a For-comprehension with the initial IO computation.
     */
    public static <A> For1<A> set(IO<A> ioA) {
        return new For1<>(Objects.requireNonNull(ioA));
    }

    public static final class For1<A> {
        private final IO<A> ioA;

        private For1(IO<A> ioA) {
            this.ioA = ioA;
        }

        public <B> For2<A, B> bind(Function<? super A, ? extends IO<B>> fB) {
            return new For2<>(ioA, Objects.requireNonNull(fB));
        }

        public <R> IO<R> yield(Function<? super A, ? extends R> f) {
            return ioA.map(f);
        }
    }

    public static final class For2<A, B> {
        private final IO<A> ioA;
        private final Function<? super A, ? extends IO<B>> fB;

        private For2(IO<A> ioA, Function<? super A, ? extends IO<B>> fB) {
            this.ioA = ioA;
            this.fB = fB;
        }

        public <C> For3<A, B, C> bind(BiFunction<? super A, ? super B, ? extends IO<C>> fC) {
            return new For3<>(ioA, fB, Objects.requireNonNull(fC));
        }

        public <R> IO<R> yield(BiFunction<? super A, ? super B, ? extends R> f) {
            Objects.requireNonNull(f);
            return ioA.flatMap(a -> 
                fB.apply(a).map(b -> f.apply(a, b))
            );
        }
    }

    public static final class For3<A, B, C> {
        private final IO<A> ioA;
        private final Function<? super A, ? extends IO<B>> fB;
        private final BiFunction<? super A, ? super B, ? extends IO<C>> fC;

        private For3(IO<A> ioA, Function<? super A, ? extends IO<B>> fB, BiFunction<? super A, ? super B, ? extends IO<C>> fC) {
            this.ioA = ioA;
            this.fB = fB;
            this.fC = fC;
        }

        public <D> For4<A, B, C, D> bind(TriFunction<? super A, ? super B, ? super C, ? extends IO<D>> fD) {
            return new For4<>(ioA, fB, fC, Objects.requireNonNull(fD));
        }

        public <R> IO<R> yield(TriFunction<? super A, ? super B, ? super C, ? extends R> f) {
            Objects.requireNonNull(f);
            return ioA.flatMap(a -> 
                fB.apply(a).flatMap(b -> 
                    fC.apply(a, b).map(c -> f.apply(a, b, c))
                )
            );
        }
    }

    public static final class For4<A, B, C, D> {
        private final IO<A> ioA;
        private final Function<? super A, ? extends IO<B>> fB;
        private final BiFunction<? super A, ? super B, ? extends IO<C>> fC;
        private final TriFunction<? super A, ? super B, ? super C, ? extends IO<D>> fD;

        private For4(
            IO<A> ioA, 
            Function<? super A, ? extends IO<B>> fB, 
            BiFunction<? super A, ? super B, ? extends IO<C>> fC,
            TriFunction<? super A, ? super B, ? super C, ? extends IO<D>> fD
        ) {
            this.ioA = ioA;
            this.fB = fB;
            this.fC = fC;
            this.fD = fD;
        }

        public <E> For5<A, B, C, D, E> bind(QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends IO<E>> fE) {
            return new For5<>(ioA, fB, fC, fD, Objects.requireNonNull(fE));
        }

        public <R> IO<R> yield(QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends R> f) {
            Objects.requireNonNull(f);
            return ioA.flatMap(a -> 
                fB.apply(a).flatMap(b -> 
                    fC.apply(a, b).flatMap(c -> 
                        fD.apply(a, b, c).map(d -> f.apply(a, b, c, d))
                    )
                )
            );
        }
    }

    public static final class For5<A, B, C, D, E> {
        private final IO<A> ioA;
        private final Function<? super A, ? extends IO<B>> fB;
        private final BiFunction<? super A, ? super B, ? extends IO<C>> fC;
        private final TriFunction<? super A, ? super B, ? super C, ? extends IO<D>> fD;
        private final QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends IO<E>> fE;

        private For5(
            IO<A> ioA, 
            Function<? super A, ? extends IO<B>> fB, 
            BiFunction<? super A, ? super B, ? extends IO<C>> fC,
            TriFunction<? super A, ? super B, ? super C, ? extends IO<D>> fD,
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends IO<E>> fE
        ) {
            this.ioA = ioA;
            this.fB = fB;
            this.fC = fC;
            this.fD = fD;
            this.fE = fE;
        }

        public <F> For6<A, B, C, D, E, F> bind(PentaFunction<? super A, ? super B, ? super C, ? super D, ? super E, ? extends IO<F>> fF) {
            return new For6<>(ioA, fB, fC, fD, fE, Objects.requireNonNull(fF));
        }

        public <R> IO<R> yield(PentaFunction<? super A, ? super B, ? super C, ? super D, ? super E, ? extends R> f) {
            Objects.requireNonNull(f);
            return ioA.flatMap(a -> 
                fB.apply(a).flatMap(b -> 
                    fC.apply(a, b).flatMap(c -> 
                        fD.apply(a, b, c).flatMap(d -> 
                            fE.apply(a, b, c, d).map(e -> f.apply(a, b, c, d, e))
                        )
                    )
                )
            );
        }
    }

    public static final class For6<A, B, C, D, E, F> {
        private final IO<A> ioA;
        private final Function<? super A, ? extends IO<B>> fB;
        private final BiFunction<? super A, ? super B, ? extends IO<C>> fC;
        private final TriFunction<? super A, ? super B, ? super C, ? extends IO<D>> fD;
        private final QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends IO<E>> fE;
        private final PentaFunction<? super A, ? super B, ? super C, ? super D, ? super E, ? extends IO<F>> fF;

        private For6(
            IO<A> ioA, 
            Function<? super A, ? extends IO<B>> fB, 
            BiFunction<? super A, ? super B, ? extends IO<C>> fC,
            TriFunction<? super A, ? super B, ? super C, ? extends IO<D>> fD,
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends IO<E>> fE,
            PentaFunction<? super A, ? super B, ? super C, ? super D, ? super E, ? extends IO<F>> fF
        ) {
            this.ioA = ioA;
            this.fB = fB;
            this.fC = fC;
            this.fD = fD;
            this.fE = fE;
            this.fF = fF;
        }

        public <R> IO<R> yield(HexaFunction<? super A, ? super B, ? super C, ? super D, ? super E, ? super F, ? extends R> f) {
            Objects.requireNonNull(f);
            return ioA.flatMap(a -> 
                fB.apply(a).flatMap(b -> 
                    fC.apply(a, b).flatMap(c -> 
                        fD.apply(a, b, c).flatMap(d -> 
                            fE.apply(a, b, c, d).flatMap(e -> 
                                fF.apply(a, b, c, d, e).map(g -> f.apply(a, b, c, d, e, g))
                            )
                        )
                    )
                )
            );
        }
    }
}
