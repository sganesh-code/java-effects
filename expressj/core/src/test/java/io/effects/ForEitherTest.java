package io.effects;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForEitherTest {

    @Test
    void testForEitherArity1() {
        Either<String, Integer> eitherA = Either.right(10);
        Either<String, Integer> result = ForEither.set(eitherA)
            .yield(a -> a + 5);

        assertTrue(result.isRight());
        assertEquals(15, result.getRight());
    }

    @Test
    void testForEitherArity2() {
        Either<String, Integer> eitherA = Either.right(10);
        Either<String, Integer> result = ForEither.set(eitherA)
            .bind(a -> Either.right(a + 5))
            .yield((a, b) -> a + b);

        assertTrue(result.isRight());
        assertEquals(25, result.getRight());
    }

    @Test
    void testForEitherArity3() {
        Either<String, Integer> eitherA = Either.right(10);
        Either<String, Integer> result = ForEither.set(eitherA)
            .bind(a -> Either.right(a + 5))
            .bind((a, b) -> Either.right(b * 2))
            .yield((a, b, c) -> a + b + c);

        assertTrue(result.isRight());
        assertEquals(55, result.getRight());
    }

    @Test
    void testForEitherArity4() {
        Either<String, Integer> eitherA = Either.right(2);
        Either<String, Integer> result = ForEither.set(eitherA)
            .bind(a -> Either.right(a * 3)) // b = 6
            .bind((a, b) -> Either.right(a + b)) // c = 8
            .bind((a, b, c) -> Either.right(c - b)) // d = 2
            .yield((a, b, c, d) -> a + b + c + d); // 2 + 6 + 8 + 2 = 18

        assertTrue(result.isRight());
        assertEquals(18, result.getRight());
    }

    @Test
    void testForEitherArity5() {
        Either<String, String> eitherA = Either.right("a");
        Either<String, String> result = ForEither.set(eitherA)
            .bind(a -> Either.right(a + "b")) // b = "ab"
            .bind((a, b) -> Either.right(b + "c")) // c = "abc"
            .bind((a, b, c) -> Either.right(c + "d")) // d = "abcd"
            .bind((a, b, c, d) -> Either.right(d + "e")) // e = "abcde"
            .yield((a, b, c, d, e) -> a + b + c + d + e);

        assertTrue(result.isRight());
        assertEquals("aababcabcdabcde", result.getRight());
    }

    @Test
    void testForEitherArity6() {
        Either<String, Integer> eitherA = Either.right(1);
        Either<String, Integer> result = ForEither.set(eitherA)
            .bind(a -> Either.right(a + 1)) // b = 2
            .bind((a, b) -> Either.right(b + 1)) // c = 3
            .bind((a, b, c) -> Either.right(c + 1)) // d = 4
            .bind((a, b, c, d) -> Either.right(d + 1)) // e = 5
            .bind((a, b, c, d, e) -> Either.right(e + 1)) // f = 6
            .yield((a, b, c, d, e, f) -> a + b + c + d + e + f); // 1 + 2 + 3 + 4 + 5 + 6 = 21

        assertTrue(result.isRight());
        assertEquals(21, result.getRight());
    }

    @Test
    void testForEitherShortCircuit() {
        Either<String, Integer> eitherA = Either.right(1);
        Either<String, Integer> result = ForEither.set(eitherA)
            .bind(a -> Either.<String, Integer>left("Boom"))
            .bind((a, b) -> Either.right(b + 1))
            .yield((a, b, c) -> a + b + c);

        assertTrue(result.isLeft());
        assertEquals("Boom", result.getLeft());
    }
}
