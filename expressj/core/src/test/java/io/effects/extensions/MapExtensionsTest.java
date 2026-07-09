package io.effects.extensions;

import io.effects.IO;
import lombok.experimental.ExtensionMethod;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@ExtensionMethod(MapExtensions.class)
class MapExtensionsTest {

    private Map<String, Integer> makeMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("A", 1);
        map.put("B", 2);
        return map;
    }

    @Test
    void testMapValues() {
        Map<String, Integer> map = makeMap();
        Map<String, String> mapped = map.mapValues(Object::toString);
        assertEquals("1", mapped.get("A"));
        assertEquals("2", mapped.get("B"));
    }

    @Test
    void testMapKeys() {
        Map<String, Integer> map = makeMap();
        Map<String, Integer> mapped = map.mapKeys(String::toLowerCase);
        assertEquals(1, mapped.get("a"));
        assertEquals(2, mapped.get("b"));
    }

    @Test
    void testBimap() {
        Map<String, Integer> map = makeMap();
        Map<String, String> mapped = map.bimap(String::toLowerCase, Object::toString);
        assertEquals("1", mapped.get("a"));
        assertEquals("2", mapped.get("b"));
    }

    @Test
    void testFoldLeft() {
        Map<String, Integer> map = makeMap();
        Integer sumOfValues = map.foldLeft(0, (acc, entry) -> acc + entry.getValue());
        assertEquals(3, sumOfValues);
    }

    @Test
    void testTraverseValuesIO() {
        Map<String, Integer> map = makeMap();
        IO<Map<String, Integer>> traversed = map.traverseValuesIO(i -> IO.of(i * 10));
        Map<String, Integer> result = traversed.unsafeRunSync();
        assertEquals(10, result.get("A"));
        assertEquals(20, result.get("B"));
    }

    @Test
    void testSequenceValuesIO() {
        Map<String, IO<Integer>> map = new HashMap<>();
        map.put("A", IO.of(1));
        map.put("B", IO.of(2));
        IO<Map<String, Integer>> sequenced = map.sequenceValuesIO();
        Map<String, Integer> result = sequenced.unsafeRunSync();
        assertEquals(1, result.get("A"));
        assertEquals(2, result.get("B"));
    }
}
