package io.effects.extensions;

import io.effects.Either;
import io.effects.IO;
import lombok.experimental.ExtensionMethod;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@ExtensionMethod(ListExtensions.class)
class ListExtensionsTest {

    @Test
    void testMap() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        List<String> mapped = list.map(Object::toString);
        assertEquals(Arrays.asList("1", "2", "3"), mapped);
    }

    @Test
    void testAs() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        List<String> as = list.as("X");
        assertEquals(Arrays.asList("X", "X", "X"), as);
    }

    @Test
    void testVoided() {
        List<Integer> list = Arrays.asList(1, 2);
        List<Void> voided = list.voided();
        assertEquals(2, voided.size());
        assertNull(voided.get(0));
        assertNull(voided.get(1));
    }

    @Test
    void testFoldLeft() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        Integer sum = list.foldLeft(0, Integer::sum);
        assertEquals(6, sum);
    }

    @Test
    void testFoldRight() {
        List<String> list = Arrays.asList("a", "b", "c");
        String concat = list.foldRight("", (item, acc) -> item + acc);
        assertEquals("abc", concat);
    }

    @Test
    void testTraverseIO() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        IO<List<Integer>> traversed = list.traverseIO(i -> IO.delay(() -> i * 2));
        List<Integer> result = traversed.unsafeRunSync();
        assertEquals(Arrays.asList(2, 4, 6), result);
    }

    @Test
    void testSequenceIO() {
        List<IO<Integer>> ioList = Arrays.asList(IO.of(1), IO.of(2), IO.of(3));
        IO<List<Integer>> sequenced = ioList.sequenceIO();
        List<Integer> result = sequenced.unsafeRunSync();
        assertEquals(Arrays.asList(1, 2, 3), result);
    }

    @Test
    void testTraverseEither() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        Either<String, List<Integer>> traversed = list.traverseEither(i -> Either.right(i * 2));
        assertTrue(traversed.isRight());
        assertEquals(Arrays.asList(2, 4, 6), traversed.getRight());

        Either<String, List<Integer>> failed = list.traverseEither(i -> i == 2 ? Either.left("err") : Either.right(i));
        assertTrue(failed.isLeft());
        assertEquals("err", failed.getLeft());
    }

    @Test
    void testSequenceEither() {
        List<Either<String, Integer>> list = Arrays.asList(Either.right(1), Either.right(2));
        Either<String, List<Integer>> sequenced = list.sequenceEither();
        assertTrue(sequenced.isRight());
        assertEquals(Arrays.asList(1, 2), sequenced.getRight());
    }

    @Test
    void testFilter() {
        List<Integer> list = Arrays.asList(1, 2, 3, 4);
        List<Integer> filtered = list.filter(i -> i % 2 == 0);
        assertEquals(Arrays.asList(2, 4), filtered);
    }

    @Test
    void testZip() {
        List<Integer> listA = Arrays.asList(1, 2);
        List<String> listB = Arrays.asList("A", "B", "C");
        List<IO.Pair<Integer, String>> zipped = listA.zip(listB);
        assertEquals(2, zipped.size());
        assertEquals(1, zipped.get(0).first());
        assertEquals("A", zipped.get(0).second());
    }
}
