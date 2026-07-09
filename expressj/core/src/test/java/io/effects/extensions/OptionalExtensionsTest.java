package io.effects.extensions;

import io.effects.Either;
import io.effects.IO;
import lombok.experimental.ExtensionMethod;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

@ExtensionMethod(OptionalExtensions.class)
class OptionalExtensionsTest {

    @Test
    void testAs() {
        Optional<Integer> opt = Optional.of(5);
        Optional<String> as = opt.as("hello");
        assertEquals("hello", as.get());

        Optional<Integer> empty = Optional.empty();
        assertTrue(empty.as("hello").isEmpty());
    }

    @Test
    void testVoided() {
        Optional<Integer> opt = Optional.of(5);
        Optional<Void> voided = opt.voided();
        assertTrue(voided.isEmpty());
    }

    @Test
    void testTraverseIO() {
        Optional<Integer> opt = Optional.of(5);
        IO<Optional<Integer>> traversed = opt.traverseIO(i -> IO.of(i * 2));
        assertEquals(10, traversed.unsafeRunSync().get());

        Optional<Integer> empty = Optional.empty();
        assertTrue(empty.traverseIO(i -> IO.of(i * 2)).unsafeRunSync().isEmpty());
    }

    @Test
    void testSequenceIO() {
        Optional<IO<Integer>> optIo = Optional.of(IO.of(5));
        IO<Optional<Integer>> sequenced = optIo.sequenceIO();
        assertEquals(5, sequenced.unsafeRunSync().get());
    }

    @Test
    void testTraverseEither() {
        Optional<Integer> opt = Optional.of(5);
        Either<String, Optional<Integer>> traversed = opt.traverseEither(i -> Either.right(i * 2));
        assertTrue(traversed.isRight());
        assertEquals(10, traversed.getRight().get());

        Either<String, Optional<Integer>> failed = opt.traverseEither(i -> Either.left("err"));
        assertTrue(failed.isLeft());
        assertEquals("err", failed.getLeft());
    }

    @Test
    void testSequenceEither() {
        Optional<Either<String, Integer>> optEither = Optional.of(Either.right(5));
        Either<String, Optional<Integer>> sequenced = optEither.sequenceEither();
        assertTrue(sequenced.isRight());
        assertEquals(5, sequenced.getRight().get());
    }
}
