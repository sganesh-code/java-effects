package io.effects;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ForIOTest {

    @Test
    void testForIOArity1() {
        IO<Integer> ioA = IO.of(10);
        IO<Integer> result = ForIO.set(ioA)
            .yield(a -> a + 5);

        assertEquals(15, result.unsafeRunSync());
    }

    @Test
    void testForIOArity2() {
        IO<Integer> ioA = IO.of(10);
        IO<Integer> result = ForIO.set(ioA)
            .bind(a -> IO.of(a + 5))
            .yield((a, b) -> a + b);

        assertEquals(25, result.unsafeRunSync());
    }

    @Test
    void testForIOArity3() {
        IO<Integer> ioA = IO.of(10);
        IO<Integer> result = ForIO.set(ioA)
            .bind(a -> IO.of(a + 5))
            .bind((a, b) -> IO.of(b * 2))
            .yield((a, b, c) -> a + b + c);

        // a = 10, b = 15, c = 30 => 10 + 15 + 30 = 55
        assertEquals(55, result.unsafeRunSync());
    }

    @Test
    void testForIOArity4() {
        IO<Integer> ioA = IO.of(2);
        IO<Integer> result = ForIO.set(ioA)
            .bind(a -> IO.of(a * 3)) // b = 6
            .bind((a, b) -> IO.of(a + b)) // c = 8
            .bind((a, b, c) -> IO.of(c - b)) // d = 2
            .yield((a, b, c, d) -> a + b + c + d); // 2 + 6 + 8 + 2 = 18

        assertEquals(18, result.unsafeRunSync());
    }

    @Test
    void testForIOArity5() {
        IO<String> ioA = IO.of("a");
        IO<String> result = ForIO.set(ioA)
            .bind(a -> IO.of(a + "b")) // b = "ab"
            .bind((a, b) -> IO.of(b + "c")) // c = "abc"
            .bind((a, b, c) -> IO.of(c + "d")) // d = "abcd"
            .bind((a, b, c, d) -> IO.of(d + "e")) // e = "abcde"
            .yield((a, b, c, d, e) -> a + b + c + d + e);

        // "a" + "ab" + "abc" + "abcd" + "abcde" = "aababcabcdabcde"
        assertEquals("aababcabcdabcde", result.unsafeRunSync());
    }

    @Test
    void testForIOArity6() {
        IO<Integer> ioA = IO.of(1);
        IO<Integer> result = ForIO.set(ioA)
            .bind(a -> IO.of(a + 1)) // b = 2
            .bind((a, b) -> IO.of(b + 1)) // c = 3
            .bind((a, b, c) -> IO.of(c + 1)) // d = 4
            .bind((a, b, c, d) -> IO.of(d + 1)) // e = 5
            .bind((a, b, c, d, e) -> IO.of(e + 1)) // f = 6
            .yield((a, b, c, d, e, f) -> a + b + c + d + e + f); // 1 + 2 + 3 + 4 + 5 + 6 = 21

        assertEquals(21, result.unsafeRunSync());
    }

    @Test
    void testForIOShortCircuit() {
        IO<Integer> ioA = IO.of(1);
        IO<Integer> result = ForIO.set(ioA)
            .bind(a -> IO.<Integer>failed(new RuntimeException("Boom")))
            .bind((a, b) -> IO.of(b + 1))
            .yield((a, b, c) -> a + b + c);

        assertThrows(RuntimeException.class, result::unsafeRunSync);
    }
}
