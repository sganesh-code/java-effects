package io.effects.extensions;

import io.effects.Either;
import io.effects.IO;
import lombok.experimental.ExtensionMethod;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@ExtensionMethod(SetExtensions.class)
class SetExtensionsTest {

    private Set<Integer> makeSet(Integer... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    private Set<String> makeSet(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    @Test
    void testMap() {
        Set<Integer> set = makeSet(1, 2, 3);
        Set<String> mapped = set.map(Object::toString);
        assertEquals(makeSet("1", "2", "3"), mapped);
    }

    @Test
    void testAs() {
        Set<Integer> set = makeSet(1, 2);
        Set<String> as = set.as("X");
        assertEquals(makeSet("X"), as); // Sets deduplicate!
    }

    @Test
    void testVoided() {
        Set<Integer> set = makeSet(1, 2);
        Set<Void> voided = set.voided();
        assertEquals(1, voided.size());
        assertTrue(voided.contains(null));
    }

    @Test
    void testFoldLeft() {
        Set<Integer> set = makeSet(1, 2, 3);
        Integer sum = set.foldLeft(0, Integer::sum);
        assertEquals(6, sum);
    }

    @Test
    void testFoldRight() {
        Set<Integer> set = makeSet(1, 2, 3);
        Integer sum = set.foldRight(0, (item, acc) -> item + acc);
        assertEquals(6, sum);
    }

    @Test
    void testTraverseIO() {
        Set<Integer> set = makeSet(1, 2, 3);
        IO<Set<Integer>> traversed = set.traverseIO(i -> IO.of(i * 2));
        Set<Integer> result = traversed.unsafeRunSync();
        assertEquals(makeSet(2, 4, 6), result);
    }

    @Test
    void testSequenceIO() {
        Set<IO<Integer>> ioSet = new HashSet<>(Arrays.asList(IO.of(1), IO.of(2)));
        IO<Set<Integer>> sequenced = ioSet.sequenceIO();
        Set<Integer> result = sequenced.unsafeRunSync();
        assertEquals(makeSet(1, 2), result);
    }

    @Test
    void testTraverseEither() {
        Set<Integer> set = makeSet(1, 2, 3);
        Either<String, Set<Integer>> traversed = set.traverseEither(i -> Either.right(i * 2));
        assertTrue(traversed.isRight());
        assertEquals(makeSet(2, 4, 6), traversed.getRight());

        Either<String, Set<Integer>> failed = set.traverseEither(i -> i == 2 ? Either.left("err") : Either.right(i));
        assertTrue(failed.isLeft());
        assertEquals("err", failed.getLeft());
    }

    @Test
    void testSequenceEither() {
        Set<Either<String, Integer>> eitherSet = new HashSet<>(Arrays.asList(Either.right(1), Either.right(2)));
        Either<String, Set<Integer>> sequenced = eitherSet.sequenceEither();
        assertTrue(sequenced.isRight());
        assertEquals(makeSet(1, 2), sequenced.getRight());
    }
}
