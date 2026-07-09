package io.effects;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A type-safe monadic For-comprehension facade for Either computations.
 * Allows flatly chaining up to 6 sequential Either steps, avoiding nested flatMaps.
 */
public final class ForEither {
    private ForEither() {}

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
     * Start a For-comprehension with the initial Either computation.
     */
    public static <L, A> For1<L, A> set(Either<L, A> eitherA) {
        return new For1<>(Objects.requireNonNull(eitherA));
    }

    public static final class For1<L, A> {
        private final Either<L, A> eitherA;

        private For1(Either<L, A> eitherA) {
            this.eitherA = eitherA;
        }

        public <B> For2<L, A, B> bind(Function<? super A, ? extends Either<L, B>> fB) {
            return new For2<>(eitherA, Objects.requireNonNull(fB));
        }

        public <R> Either<L, R> yield(Function<? super A, ? extends R> f) {
            return eitherA.map(f);
        }
    }

    public static final class For2<L, A, B> {
        private final Either<L, A> eitherA;
        private final Function<? super A, ? extends Either<L, B>> fB;

        private For2(Either<L, A> eitherA, Function<? super A, ? extends Either<L, B>> fB) {
            this.eitherA = eitherA;
            this.fB = fB;
        }

        public <C> For3<L, A, B, C> bind(BiFunction<? super A, ? super B, ? extends Either<L, C>> fC) {
            return new For3<>(eitherA, fB, Objects.requireNonNull(fC));
        }

        public <R> Either<L, R> yield(BiFunction<? super A, ? super B, ? extends R> f) {
            Objects.requireNonNull(f);
            return eitherA.flatMap(a -> 
                fB.apply(a).map(b -> f.apply(a, b))
            );
        }
    }

    public static final class For3<L, A, B, C> {
        private final Either<L, A> eitherA;
        private final Function<? super A, ? extends Either<L, B>> fB;
        private final BiFunction<? super A, ? super B, ? extends Either<L, C>> fC;

        private For3(Either<L, A> eitherA, Function<? super A, ? extends Either<L, B>> fB, BiFunction<? super A, ? super B, ? extends Either<L, C>> fC) {
            this.eitherA = eitherA;
            this.fB = fB;
            this.fC = fC;
        }

        public <D> For4<L, A, B, C, D> bind(TriFunction<? super A, ? super B, ? super C, ? extends Either<L, D>> fD) {
            return new For4<>(eitherA, fB, fC, Objects.requireNonNull(fD));
        }

        public <R> Either<L, R> yield(TriFunction<? super A, ? super B, ? super C, ? extends R> f) {
            Objects.requireNonNull(f);
            return eitherA.flatMap(a -> 
                fB.apply(a).flatMap(b -> 
                    fC.apply(a, b).map(c -> f.apply(a, b, c))
                )
            );
        }
    }

    public static final class For4<L, A, B, C, D> {
        private final Either<L, A> eitherA;
        private final Function<? super A, ? extends Either<L, B>> fB;
        private final BiFunction<? super A, ? super B, ? extends Either<L, C>> fC;
        private final TriFunction<? super A, ? super B, ? super C, ? extends Either<L, D>> fD;

        private For4(
            Either<L, A> eitherA, 
            Function<? super A, ? extends Either<L, B>> fB, 
            BiFunction<? super A, ? super B, ? extends Either<L, C>> fC,
            TriFunction<? super A, ? super B, ? super C, ? extends Either<L, D>> fD
        ) {
            this.eitherA = eitherA;
            this.fB = fB;
            this.fC = fC;
            this.fD = fD;
        }

        public <E> For5<L, A, B, C, D, E> bind(QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Either<L, E>> fE) {
            return new For5<>(eitherA, fB, fC, fD, Objects.requireNonNull(fE));
        }

        public <R> Either<L, R> yield(QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends R> f) {
            Objects.requireNonNull(f);
            return eitherA.flatMap(a -> 
                fB.apply(a).flatMap(b -> 
                    fC.apply(a, b).flatMap(c -> 
                        fD.apply(a, b, c).map(d -> f.apply(a, b, c, d))
                    )
                )
            );
        }
    }

    public static final class For5<L, A, B, C, D, E> {
        private final Either<L, A> eitherA;
        private final Function<? super A, ? extends Either<L, B>> fB;
        private final BiFunction<? super A, ? super B, ? extends Either<L, C>> fC;
        private final TriFunction<? super A, ? super B, ? super C, ? extends Either<L, D>> fD;
        private final QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Either<L, E>> fE;

        private For5(
            Either<L, A> eitherA, 
            Function<? super A, ? extends Either<L, B>> fB, 
            BiFunction<? super A, ? super B, ? extends Either<L, C>> fC,
            TriFunction<? super A, ? super B, ? super C, ? extends Either<L, D>> fD,
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Either<L, E>> fE
        ) {
            this.eitherA = eitherA;
            this.fB = fB;
            this.fC = fC;
            this.fD = fD;
            this.fE = fE;
        }

        public <F> For6<L, A, B, C, D, E, F> bind(PentaFunction<? super A, ? super B, ? super C, ? super D, ? super E, ? extends Either<L, F>> fF) {
            return new For6<>(eitherA, fB, fC, fD, fE, Objects.requireNonNull(fF));
        }

        public <R> Either<L, R> yield(PentaFunction<? super A, ? super B, ? super C, ? super D, ? super E, ? extends R> f) {
            Objects.requireNonNull(f);
            return eitherA.flatMap(a -> 
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

    public static final class For6<L, A, B, C, D, E, F> {
        private final Either<L, A> eitherA;
        private final Function<? super A, ? extends Either<L, B>> fB;
        private final BiFunction<? super A, ? super B, ? extends Either<L, C>> fC;
        private final TriFunction<? super A, ? super B, ? super C, ? extends Either<L, D>> fD;
        private final QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Either<L, E>> fE;
        private final PentaFunction<? super A, ? super B, ? super C, ? super D, ? super E, ? extends Either<L, F>> fF;

        private For6(
            Either<L, A> eitherA, 
            Function<? super A, ? extends Either<L, B>> fB, 
            BiFunction<? super A, ? super B, ? extends Either<L, C>> fC,
            TriFunction<? super A, ? super B, ? super C, ? extends Either<L, D>> fD,
            QuadFunction<? super A, ? super B, ? super C, ? super D, ? extends Either<L, E>> fE,
            PentaFunction<? super A, ? super B, ? super C, ? super D, ? super E, ? extends Either<L, F>> fF
        ) {
            this.eitherA = eitherA;
            this.fB = fB;
            this.fC = fC;
            this.fD = fD;
            this.fE = fE;
            this.fF = fF;
        }

        public <R> Either<L, R> yield(HexaFunction<? super A, ? super B, ? super C, ? super D, ? super E, ? super F, ? extends R> f) {
            Objects.requireNonNull(f);
            return eitherA.flatMap(a -> 
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
