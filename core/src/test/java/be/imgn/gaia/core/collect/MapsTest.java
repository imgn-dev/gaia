package be.imgn.gaia.core.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MapsTest {

    static Stream<Arguments> everyFactory() {
        return Stream.of(
                Arguments.of(Maps.map(), List.of()),
                Arguments.of(Maps.map(1, "1"), List.of(1)),
                Arguments.of(Maps.map(2, "2", 1, "1"), List.of(2, 1)),
                Arguments.of(Maps.map(3, "3", 1, "1", 2, "2"), List.of(3, 1, 2)),
                Arguments.of(Maps.map(4, "4", 3, "3", 2, "2", 1, "1"), List.of(4, 3, 2, 1)),
                Arguments.of(Maps.map(5, "5", 1, "1", 4, "4", 2, "2", 3, "3"), List.of(5, 1, 4, 2, 3)),
                Arguments.of(Maps.map(6, "6", 5, "5", 4, "4", 3, "3", 2, "2", 1, "1"), List.of(6, 5, 4, 3, 2, 1)),
                Arguments.of(
                        Maps.map(7, "7", 1, "1", 6, "6", 2, "2", 5, "5", 3, "3", 4, "4"), List.of(7, 1, 6, 2, 5, 3, 4)),
                Arguments.of(
                        Maps.map(8, "8", 7, "7", 6, "6", 5, "5", 4, "4", 3, "3", 2, "2", 1, "1"),
                        List.of(8, 7, 6, 5, 4, 3, 2, 1)),
                Arguments.of(
                        Maps.map(9, "9", 1, "1", 8, "8", 2, "2", 7, "7", 3, "3", 6, "6", 4, "4", 5, "5"),
                        List.of(9, 1, 8, 2, 7, 3, 6, 4, 5)),
                Arguments.of(
                        Maps.map(10, "10", 9, "9", 8, "8", 7, "7", 6, "6", 5, "5", 4, "4", 3, "3", 2, "2", 1, "1"),
                        List.of(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)),
                Arguments.of(
                        Maps.mapOfEntries(Map.entry(3, "3"), Map.entry(1, "1"), Map.entry(2, "2")), List.of(3, 1, 2)),
                Arguments.of(Maps.mapCopy(linkedMap(3, 1, 2)), List.of(3, 1, 2)),
                Arguments.of(Maps.mapCopy(Maps.map(3, "3", 1, "1", 2, "2")), List.of(3, 1, 2)),
                Arguments.of(
                        Stream.of(3, 1, 2).collect(Maps.toMap(Function.identity(), String::valueOf)), List.of(3, 1, 2)),
                Arguments.of(
                        Stream.of(3, 1, 2, 1).collect(Maps.toMap(Function.identity(), String::valueOf, (a, b) -> a)),
                        List.of(3, 1, 2)));
    }

    /** An empty map, one with a single entry, and a larger one: however they were built, they are the same class. */
    static Stream<Arguments> someMaps() {
        return Stream.of(
                Arguments.of(Maps.map(), List.of()),
                Arguments.of(Maps.map(1, "1"), List.of(1)),
                Arguments.of(Maps.map(3, "3", 1, "1", 2, "2"), List.of(3, 1, 2)));
    }

    private static Map<Integer, String> linkedMap(Integer... keys) {
        var map = new LinkedHashMap<Integer, String>();
        for (Integer key : keys) {
            map.put(key, String.valueOf(key));
        }
        return map;
    }

    private static Map<Integer, String> expected(List<Integer> order) {
        return linkedMap(order.toArray(new Integer[0]));
    }

    @ParameterizedTest
    @MethodSource("everyFactory")
    void iteratesInTheGivenOrder(Map<Integer, String> map, List<Integer> order) {
        List<String> values = order.stream().map(String::valueOf).toList();

        assertThat(map.keySet()).containsExactlyElementsOf(order);
        assertThat(map.values()).containsExactlyElementsOf(values);
        assertThat(map.entrySet()).containsExactlyElementsOf(expected(order).entrySet());
        assertThat(map.keySet().stream().toList()).isEqualTo(order);
        assertThat(map.values().toArray()).containsExactly(values.toArray());
        var visited = new ArrayList<Integer>();
        map.forEach((key, value) -> visited.add(key));
        assertThat(visited).isEqualTo(order);
    }

    @ParameterizedTest
    @MethodSource("someMaps")
    void equalsTheMapOfWithTheSameEntries(Map<Integer, String> map, List<Integer> order) {
        Map<Integer, String> jdk = Map.copyOf(expected(order));

        assertThat(map).isEqualTo(jdk);
        assertThat(jdk).isEqualTo(map);
        assertThat(map).hasSameHashCodeAs(jdk);
        assertThat(map.keySet()).isEqualTo(jdk.keySet());
        assertThat(map.entrySet()).isEqualTo(jdk.entrySet());
        assertThat(map).hasToString(expected(order).toString());
    }

    @ParameterizedTest
    @MethodSource("someMaps")
    void looksUpEveryKeyAndValue(Map<Integer, String> map, List<Integer> order) {
        assertThat(map).hasSize(order.size());
        assertThat(map.isEmpty()).isEqualTo(order.isEmpty());
        for (Integer key : order) {
            assertThat(map.get(key)).isEqualTo(String.valueOf(key));
            assertThat(map.containsKey(key)).isTrue();
            assertThat(map.containsValue(String.valueOf(key))).isTrue();
            assertThat(map.entrySet()).contains(Map.entry(key, String.valueOf(key)));
        }
        assertThat(map.get(0)).isNull();
        assertThat(map.getOrDefault(0, "default")).isEqualTo("default");
        assertThat(map.containsKey(0)).isFalse();
        assertThat(map.containsValue("0")).isFalse();
        assertThat(map.entrySet()).doesNotContain(Map.entry(1, "0"), Map.entry(0, "0"));
        Set<?> entries = map.entrySet();
        assertThat(entries.contains("1=1")).isFalse();
    }

    @ParameterizedTest
    @MethodSource("someMaps")
    void reportsOrderedSpliterators(Map<Integer, String> map, List<Integer> order) {
        int sized = Spliterator.SIZED | Spliterator.SUBSIZED;

        assertThat(map.keySet().spliterator().characteristics())
                .isEqualTo(Spliterator.ORDERED | Spliterator.DISTINCT | sized);
        assertThat(map.entrySet().spliterator().characteristics())
                .isEqualTo(Spliterator.ORDERED | Spliterator.DISTINCT | sized);
        assertThat(map.values().spliterator().hasCharacteristics(Spliterator.ORDERED | sized))
                .isTrue();
    }

    @ParameterizedTest
    @MethodSource("someMaps")
    void survivesSerializationWithItsOrder(Map<Integer, String> map, List<Integer> order)
            throws IOException, ClassNotFoundException {
        Object copy = deserialize(serialize(map));

        assertThat(copy).isInstanceOf(Map.class).isEqualTo(map);
        assertThat(List.copyOf(((Map<?, ?>) copy).keySet())).isEqualTo(order);
    }

    @Test
    void rejectsATamperedSizeWithoutAllocatingIt() throws IOException {
        byte[] tampered =
                TamperedStreams.replacingFirstInt(serialize(Maps.map(3, "3", 1, "1", 2, "2")), 3, 0x7fff_fff0);

        assertThatIOException().isThrownBy(() -> deserialize(tampered));
    }

    private static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    @Nested
    class LikeMapOf {

        @Test
        void rejectsDuplicateKeys() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Maps.map("a", 1, "b", 2, "a", 3))
                    .withMessage("duplicate key: a");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Maps.mapOfEntries(Map.entry("a", 1), Map.entry("a", 1)))
                    .withMessage("duplicate key: a");
        }

        // Passing null is the point.
        @Test
        @SuppressWarnings("NullAway")
        void rejectsNullKeysAndValues() {
            assertThatNullPointerException().isThrownBy(() -> Maps.map(null, 1)).withMessage("k1");
            assertThatNullPointerException()
                    .isThrownBy(() -> Maps.map("a", 1, "b", null))
                    .withMessage("v2");
        }

        // Passing null is the point.
        @Test
        @SuppressWarnings("NullAway")
        void throwsWhenQueriedWithNull() {
            Map<String, Integer> map = Maps.map("a", 1, "b", 2);

            assertThatNullPointerException().isThrownBy(() -> map.get(null));
            assertThatNullPointerException().isThrownBy(() -> map.getOrDefault(null, 0));
            assertThatNullPointerException().isThrownBy(() -> map.containsKey(null));
            assertThatNullPointerException().isThrownBy(() -> map.containsValue(null));
            assertThatNullPointerException().isThrownBy(() -> map.keySet().contains(null));
            assertThatNullPointerException().isThrownBy(() -> map.values().contains(null));
            assertThatNullPointerException().isThrownBy(() -> map.entrySet().contains(null));
        }

        @Test
        void isNotEqualToAMapContainingNull() {
            var withNull = new HashMap<String, Integer>();
            withNull.put("a", 1);
            withNull.put(null, 2);

            assertThat(Maps.map("a", 1, "b", 2)).isNotEqualTo(withNull);
            assertThat(Maps.map("a", 1, "b", 2).entrySet()).isNotEqualTo(withNull.entrySet());
        }
    }

    @Nested
    class LikeMapOfEntries {

        @Test
        void doesNotKeepTheEntries() {
            var entry = new SimpleEntry<>("a", 1);
            Map<String, Integer> map = Maps.mapOfEntries(entry);

            entry.setValue(2);

            assertThat(map).containsExactly(Map.entry("a", 1));
        }

        // Passing null is the point.
        @Test
        @SuppressWarnings("NullAway")
        void rejectsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Maps.mapOfEntries((Map.Entry<String, Integer>[]) null))
                    .withMessage("entries");
            assertThatNullPointerException()
                    .isThrownBy(() -> Maps.mapOfEntries(Map.entry("a", 1), null))
                    .withMessage("entries[1]");
            assertThatNullPointerException()
                    .isThrownBy(() -> Maps.mapOfEntries(new SimpleEntry<String, Integer>(null, 1)))
                    .withMessage("entries[0] has a null key");
            assertThatNullPointerException()
                    .isThrownBy(() -> Maps.mapOfEntries(new SimpleEntry<String, Integer>("a", null)))
                    .withMessage("entries[0] has a null value");
        }
    }

    @Nested
    class LikeMapCopyOf {

        @Test
        void doesNotFollowLaterChangesToTheSource() {
            var source = new LinkedHashMap<String, Integer>();
            source.put("b", 2);
            source.put("a", 1);
            Map<String, Integer> copy = Maps.mapCopy(source);

            source.put("c", 3);
            source.put("b", 4);

            assertThat(copy).containsExactly(Map.entry("b", 2), Map.entry("a", 1));
        }

        @Test
        void rejectsKeysThatAreEqualButNotIdentical() {
            var source = new IdentityHashMap<String, Integer>();
            source.put(new String("a"), 1);
            source.put(new String("a"), 2);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Maps.mapCopy(source))
                    .withMessage("duplicate key: a");
        }

        // Passing null is the point.
        @Test
        @SuppressWarnings("NullAway")
        void rejectsNull() {
            var nullKey = new HashMap<String, Integer>();
            nullKey.put(null, 1);
            var nullValue = new HashMap<String, Integer>();
            nullValue.put("a", null);

            assertThatNullPointerException()
                    .isThrownBy(() -> Maps.mapCopy((Map<String, Integer>) null))
                    .withMessage("map");
            assertThatNullPointerException()
                    .isThrownBy(() -> Maps.mapCopy(nullKey))
                    .withMessage("map contains a null key");
            assertThatNullPointerException()
                    .isThrownBy(() -> Maps.mapCopy(nullValue))
                    .withMessage("map contains a null value");
        }
    }

    @Nested
    class Collecting {

        @Test
        void rejectsDuplicateKeys() {
            assertThatIllegalStateException()
                    .isThrownBy(() -> Stream.of("a1", "b1", "a2").collect(Maps.toMap(s -> s.charAt(0), s -> s)))
                    .withMessage("duplicate key a (attempted merging values a1 and a2)");
        }

        @Test
        void mergesDuplicateKeysWhereTheyFirstAppear() {
            Map<Character, String> map = Stream.of("b1", "a1", "b2", "c1", "a2")
                    .collect(Maps.toMap(s -> s.charAt(0), s -> s, (left, right) -> left + "+" + right));

            assertThat(map).containsExactly(Map.entry('b', "b1+b2"), Map.entry('a', "a1+a2"), Map.entry('c', "c1"));
        }

        @Test
        void keepsTheEncounterOrderOfAParallelStream() {
            List<Integer> numbers = IntStream.rangeClosed(1, 10_000)
                    .map(i -> 10_001 - i)
                    .boxed()
                    .toList();

            assertThat(numbers.parallelStream()
                            .collect(Maps.toMap(Function.identity(), String::valueOf))
                            .keySet())
                    .containsExactlyElementsOf(numbers);
            assertThat(numbers.parallelStream()
                            .collect(Maps.toMap(i -> i % 100, String::valueOf, (left, right) -> left))
                            .entrySet())
                    .containsExactlyElementsOf(numbers.subList(0, 100).stream()
                            .map(i -> Map.entry(i % 100, String.valueOf(i)))
                            .toList());
        }

        @Test
        void detectsDuplicateKeysAcrossParallelChunks() {
            List<Integer> numbers = IntStream.rangeClosed(1, 10_000).boxed().toList();

            assertThatIllegalStateException()
                    .isThrownBy(() -> numbers.parallelStream().collect(Maps.toMap(i -> i % 9_999, i -> i)));
        }

        @Test
        void collectsAnEmptyStream() {
            assertThat(Stream.<String>empty().collect(Maps.toMap(s -> s, s -> s)))
                    .isEmpty();
        }

        // Mapping to null is the point.
        @Test
        @SuppressWarnings("NullAway")
        void rejectsNullKeysAndValues() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Stream.of("a").collect(Maps.toMap(s -> null, s -> s)))
                    .withMessage("key");
            assertThatNullPointerException()
                    .isThrownBy(() -> Stream.of("a").collect(Maps.toMap(s -> s, s -> null)))
                    .withMessage("value");
            assertThatNullPointerException()
                    .isThrownBy(() -> Stream.of("a").collect(Maps.toMap(s -> s, s -> null, (left, right) -> left)))
                    .withMessage("value");
        }

        // Passing null is the point.
        @Test
        @SuppressWarnings("NullAway")
        void rejectsNullFunctions() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Maps.toMap(null, Function.identity()))
                    .withMessage("keyMapper");
            assertThatNullPointerException()
                    .isThrownBy(() -> Maps.toMap(Function.identity(), null))
                    .withMessage("valueMapper");
            assertThatNullPointerException()
                    .isThrownBy(() -> Maps.toMap(null, Function.identity(), (left, right) -> left))
                    .withMessage("keyMapper");
            assertThatNullPointerException()
                    .isThrownBy(() -> Maps.toMap(Function.identity(), null, (left, right) -> left))
                    .withMessage("valueMapper");
            assertThatNullPointerException()
                    .isThrownBy(() -> Maps.toMap(Function.identity(), Function.identity(), null))
                    .withMessage("merger");
        }
    }

    @Nested
    class Unmodifiable {

        static Stream<Arguments> mutators() {
            return Stream.<Consumer<Map<String, Integer>>>of(
                            map -> map.put("c", 3),
                            map -> map.put("a", 1),
                            map -> map.putAll(Map.of()),
                            map -> map.putIfAbsent("a", 1),
                            map -> map.remove("a"),
                            map -> map.remove("z"),
                            map -> map.remove("a", 1),
                            map -> map.replace("a", 1),
                            map -> map.replace("a", 1, 1),
                            map -> map.replaceAll((key, value) -> value),
                            map -> map.compute("a", (key, value) -> value),
                            map -> map.computeIfAbsent("a", key -> 1),
                            map -> map.computeIfPresent("z", (key, value) -> value),
                            map -> map.merge("a", 1, (left, right) -> left),
                            Map::clear,
                            map -> map.keySet().remove("z"),
                            map -> map.keySet().clear(),
                            map -> map.values().remove(9),
                            map -> map.values().removeIf(value -> false),
                            map -> map.entrySet().remove(Map.entry("z", 9)),
                            map -> map.entrySet().retainAll(Set.of()),
                            map -> map.entrySet().add(Map.entry("a", 1)),
                            map -> map.entrySet().iterator().next().setValue(1),
                            map -> {
                                var iterator = map.entrySet().iterator();
                                iterator.next();
                                iterator.remove();
                            },
                            map -> {
                                var iterator = map.values().iterator();
                                iterator.next();
                                iterator.remove();
                            })
                    .map(Arguments::of);
        }

        @ParameterizedTest
        @MethodSource("mutators")
        void throwsOnEveryMutationEvenANoOp(Consumer<Map<String, Integer>> mutation) {
            Map<String, Integer> map = Maps.map("a", 1, "b", 2);

            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> mutation.accept(map));
            assertThat(map).containsExactly(Map.entry("a", 1), Map.entry("b", 2));
        }

        @Test
        void throwsEvenWhenEmpty() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> Maps.map().clear());
        }
    }
}
