package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HashListMultimapTest {

    private final HashListMultimap<String, Integer> multimap = HashListMultimap.create();

    /** Checks that the size, the views and the map agree, and that no key is left without values. */
    static void assertConsistent(Multimap<?, ?> multimap) {
        var total = 0;
        for (var values : multimap.asMap().values()) {
            assertThat(values).isNotEmpty();
            total += values.size();
        }
        assertThat(multimap.size()).isEqualTo(total);
        assertThat(multimap.isEmpty()).isEqualTo(total == 0);
        assertThat(multimap.values()).hasSize(total);
        assertThat(multimap.entries()).hasSize(total);
        assertThat(new ArrayList<>(multimap.entries())).hasSize(total);
        assertThat(multimap.keySet()).isEqualTo(multimap.asMap().keySet());
    }

    @Test
    void keepsTheValuesOfAKeyInOrderWithDuplicates() {
        assertThat(multimap.put("a", 1)).isTrue();
        assertThat(multimap.put("a", 2)).isTrue();
        assertThat(multimap.put("a", 1)).isTrue();
        assertThat(multimap.put("b", 3)).isTrue();

        assertThat(multimap.get("a")).containsExactly(1, 2, 1);
        assertThat(multimap.get("b")).containsExactly(3);
        assertThat(multimap.get("c")).isEmpty();
        assertThat(multimap).hasToString("{a=[1, 2, 1], b=[3]}");
        assertThat(multimap.size()).isEqualTo(4);
        assertConsistent(multimap);
    }

    @Test
    void answersQueries() {
        multimap.putAll("a", List.of(1, 2));
        multimap.put("b", 3);

        assertThat(multimap.containsKey("a")).isTrue();
        assertThat(multimap.containsKey("c")).isFalse();
        assertThat(multimap.containsValue(3)).isTrue();
        assertThat(multimap.containsValue(4)).isFalse();
        assertThat(multimap.containsEntry("a", 2)).isTrue();
        assertThat(multimap.containsEntry("b", 2)).isFalse();
        assertThat(multimap.containsEntry("c", 2)).isFalse();
    }

    @Test
    void callsAnActionWithEachEntryGroupedByKey() {
        multimap.putAll("a", List.of(1, 2));
        multimap.put("b", 3);
        multimap.put("a", 4);
        var visited = new ArrayList<String>();

        multimap.forEach((key, value) -> visited.add(key + value));

        assertThat(visited).containsExactlyInAnyOrder("a1", "a2", "a4", "b3");
        assertThat(String.join("", visited)).containsAnyOf("a1a2a4b3", "b3a1a2a4");
    }

    @Test
    void removesAnEntryAndThenTheKey() {
        multimap.putAll("a", List.of(1, 2, 1));

        assertThat(multimap.remove("a", 1)).isTrue();
        assertThat(multimap.get("a")).containsExactly(2, 1);
        assertThat(multimap.remove("a", 3)).isFalse();
        assertThat(multimap.remove("b", 1)).isFalse();
        assertThat(multimap.remove("a", 2)).isTrue();
        assertThat(multimap.remove("a", 1)).isTrue();

        assertThat(multimap.containsKey("a")).isFalse();
        assertThat(multimap.isEmpty()).isTrue();
        assertConsistent(multimap);
    }

    @Test
    void removesAllTheValuesOfAKey() {
        multimap.putAll("a", List.of(1, 2));
        multimap.put("b", 3);

        List<Integer> removed = multimap.removeAll("a");

        assertThat(removed).containsExactly(1, 2);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> removed.add(3));
        assertThat(multimap.removeAll("a")).isEmpty();
        assertThat(multimap.keySet()).containsExactly("b");
        assertConsistent(multimap);
    }

    @Test
    void replacesTheValuesOfAKey() {
        multimap.putAll("a", List.of(1, 2));

        assertThat(multimap.replaceValues("a", List.of(3, 3, 4))).containsExactly(1, 2);
        assertThat(multimap.get("a")).containsExactly(3, 3, 4);
        assertThat(multimap.replaceValues("b", List.of(5))).isEmpty();
        assertThat(multimap.replaceValues("a", List.of())).containsExactly(3, 3, 4);
        assertThat(multimap.containsKey("a")).isFalse();
        assertThat(multimap.replaceValues("c", List.of())).isEmpty();
        assertThat(multimap.size()).isEqualTo(1);
        assertConsistent(multimap);
    }

    @Test
    void putsTheEntriesOfAnotherMultimapAndOfItself() {
        multimap.putAll("a", List.of(1, 2));
        var other = HashListMultimap.<String, Integer>create();
        other.put("a", 3);
        other.put("b", 4);

        assertThat(multimap.putAll(other)).isTrue();
        assertThat(multimap.putAll(HashListMultimap.create())).isFalse();
        assertThat(multimap.putAll(multimap)).isTrue();

        assertThat(multimap.get("a")).containsExactly(1, 2, 3, 1, 2, 3);
        assertThat(multimap.get("b")).containsExactly(4, 4);
        assertConsistent(multimap);
    }

    @Test
    void copiesAnotherMultimap() {
        multimap.putAll("a", List.of(1, 2));

        var copy = HashListMultimap.create(multimap);
        multimap.put("a", 3);

        assertThat(copy.get("a")).containsExactly(1, 2);
    }

    @Test
    void clears() {
        multimap.putAll("a", List.of(1, 2));
        multimap.put("b", 3);

        multimap.clear();

        assertThat(multimap.isEmpty()).isTrue();
        assertThat(multimap.size()).isZero();
        assertConsistent(multimap);
    }

    @Test
    void equalsAMultimapWithTheSameKeysAndValuesInTheSameOrder() {
        multimap.putAll("a", List.of(1, 2));
        multimap.put("b", 3);
        var same = HashListMultimap.<String, Integer>create();
        same.put("b", 3);
        same.putAll("a", List.of(1, 2));
        var reordered = HashListMultimap.<String, Integer>create();
        reordered.putAll("a", List.of(2, 1));
        reordered.put("b", 3);

        assertThat(multimap).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(multimap).isNotEqualTo(reordered);
        assertThat(multimap).hasSameHashCodeAs(Map.of("a", List.of(1, 2), "b", List.of(3)));
        assertThat(multimap.asMap()).isEqualTo(Map.of("a", List.of(1, 2), "b", List.of(3)));
        assertThat(multimap).isNotEqualTo(Map.of("a", List.of(1, 2), "b", List.of(3)));
    }

    @Test
    void survivesSerialization() throws IOException, ClassNotFoundException {
        multimap.putAll("a", List.of(1, 2, 1));
        multimap.put("b", 3);

        Object copy = deserialize(serialize(multimap));

        assertThat(copy).isInstanceOf(HashListMultimap.class).isEqualTo(multimap);
        assertConsistent((Multimap<?, ?>) copy);
    }

    @Test
    void rejectsATamperedNumberOfKeysWithoutAllocatingIt() throws IOException {
        multimap.putAll("a", List.of(1, 2, 1));
        multimap.put("b", 3);

        byte[] tampered = TamperedStreams.replacingFirstInt(serialize(multimap), 2, 0x7fff_fff0);

        assertThatIOException().isThrownBy(() -> deserialize(tampered));
    }

    @Test
    void rejectsATamperedNumberOfValuesWithoutAllocatingIt() throws IOException {
        multimap.putAll("a", List.of(1, 2, 1));
        multimap.put("b", 3);

        byte[] tampered = TamperedStreams.replacingFirstInt(serialize(multimap), 3, 0x7fff_fff0);

        assertThatIOException().isThrownBy(() -> deserialize(tampered));
    }

    static byte[] serialize(Object object) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        return bytes.toByteArray();
    }

    static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        }
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullKeysAndValuesButNotNullQueries() {
        assertThatNullPointerException()
                .isThrownBy(() -> HashListMultimap.create(null))
                .withMessage("multimap");
        assertThatNullPointerException().isThrownBy(() -> multimap.putAll(null)).withMessage("multimap");
        assertThatNullPointerException().isThrownBy(() -> multimap.put(null, 1)).withMessage("key");
        assertThatNullPointerException()
                .isThrownBy(() -> multimap.put("a", null))
                .withMessage("value");
        assertThatNullPointerException().isThrownBy(() -> multimap.get(null)).withMessage("key");
        assertThatNullPointerException()
                .isThrownBy(() -> multimap.putAll("a", Arrays.asList(1, null)))
                .withMessage("values contains null");
        assertThatNullPointerException()
                .isThrownBy(() -> multimap.replaceValues("a", Arrays.asList(1, null)))
                .withMessage("values contains null");
        assertThat(multimap.isEmpty()).isTrue();

        multimap.put("a", 1);
        assertThat(multimap.containsKey(null)).isFalse();
        assertThat(multimap.containsValue(null)).isFalse();
        assertThat(multimap.containsEntry("a", null)).isFalse();
        assertThat(multimap.remove(null, 1)).isFalse();
        assertThat(multimap.removeAll(null)).isEmpty();
        assertThat(multimap.get("a").contains(null)).isFalse();
        assertThat(multimap.asMap().get(null)).isNull();
        assertThat(multimap.asMap().containsValue(null)).isFalse();
    }

    @Nested
    class ValuesOfAKey {

        @Test
        void followTheKeyAsItLeavesAndComesBack() {
            List<Integer> values = multimap.get("a");

            multimap.put("a", 1);
            assertThat(values).containsExactly(1);
            multimap.removeAll("a");
            assertThat(values).isEmpty();
            multimap.put("a", 2);
            assertThat(values).containsExactly(2);
        }

        @Test
        void addToTheMultimap() {
            List<Integer> values = multimap.get("a");

            values.add(1);
            values.addAll(List.of(2, 3));
            values.add(0, 0);

            assertThat(multimap.get("a")).containsExactly(0, 1, 2, 3);
            assertThat(multimap.size()).isEqualTo(4);
            assertConsistent(multimap);
        }

        @Test
        void doNotAddTheKeyForAnInvalidIndex() {
            List<Integer> values = multimap.get("a");

            assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> values.add(1, 1));
            assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> values.set(0, 1));
            assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> values.get(0));
            assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> values.remove(/* index */ 0));

            assertThat(multimap.containsKey("a")).isFalse();
            assertConsistent(multimap);
        }

        @Test
        void removeFromTheMultimapAndThenTheKey() {
            multimap.putAll("a", List.of(1, 2, 3, 4, 5));
            List<Integer> values = multimap.get("a");

            assertThat(values.remove(Integer.valueOf(2))).isTrue();
            assertThat(values.remove(/* index */ 0)).isEqualTo(1);
            values.subList(0, 2).clear();
            assertThat(multimap.get("a")).containsExactly(5);
            values.removeIf(value -> value == 5);

            assertThat(multimap.containsKey("a")).isFalse();
            assertConsistent(multimap);
        }

        @Test
        void removeThroughTheirIterators() {
            multimap.putAll("a", List.of(1, 2, 3));
            var iterator = multimap.get("a").listIterator();

            iterator.next();
            iterator.remove();
            iterator.next();
            iterator.set(4);
            iterator.add(5);

            assertThat(multimap.get("a")).containsExactly(4, 5, 3);
            assertConsistent(multimap);
        }

        @Test
        void clearTheKey() {
            multimap.putAll("a", List.of(1, 2));
            multimap.put("b", 3);

            multimap.get("a").clear();
            multimap.get("c").clear();

            assertThat(multimap.keySet()).containsExactly("b");
            assertConsistent(multimap);
        }

        @Test
        void areSearchedAndSorted() {
            multimap.putAll("a", List.of(3, 1, 2, 1));
            List<Integer> values = multimap.get("a");

            assertThat(values.indexOf(1)).isEqualTo(1);
            assertThat(values.lastIndexOf(1)).isEqualTo(3);
            assertThat(multimap.get("b").indexOf(1)).isEqualTo(-1);
            values.sort(Comparator.naturalOrder());
            values.replaceAll(value -> value * 10);

            assertThat(multimap.get("a")).containsExactly(10, 10, 20, 30);
            assertConsistent(multimap);
        }

        // Passing null is the point.
        @Test
        @SuppressWarnings("NullAway")
        void rejectNullValues() {
            assertThatNullPointerException().isThrownBy(() -> multimap.get("a").add(null));
            multimap.put("a", 1);
            assertThatNullPointerException().isThrownBy(() -> multimap.get("a").set(0, null));
        }
    }

    @Nested
    class Views {

        @Test
        void removeKeysWithAllTheirValues() {
            multimap.putAll("a", List.of(1, 2));
            multimap.putAll("b", List.of(3, 4, 5));
            multimap.put("c", 6);

            assertThat(multimap.keySet().remove("a")).isTrue();
            assertThat(multimap.keySet().remove("z")).isFalse();
            var keys = multimap.keySet().iterator();
            keys.next();
            keys.remove();

            assertThat(multimap.keySet()).hasSize(1);
            assertThat(multimap.size()).isIn(1, 3);
            assertConsistent(multimap);
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> multimap.keySet().add("d"));
        }

        @Test
        void iterateOverValuesGroupedByKeyAndRemoveThem() {
            multimap.putAll("a", List.of(1, 2));
            multimap.put("b", 3);

            assertThat(multimap.values()).containsExactlyInAnyOrder(1, 2, 3);
            assertThat(multimap.values().contains(3)).isTrue();
            multimap.values().removeIf(value -> value != 2);

            assertThat(multimap.asMap()).isEqualTo(Map.of("a", List.of(2)));
            assertConsistent(multimap);
        }

        @Test
        void iterateOverEntriesAndRemoveThem() {
            multimap.putAll("a", List.of(1, 1));
            multimap.put("b", 3);

            assertThat(multimap.entries())
                    .containsExactlyInAnyOrder(Map.entry("a", 1), Map.entry("a", 1), Map.entry("b", 3));
            assertThat(multimap.entries().contains(Map.entry("b", 3))).isTrue();
            assertThat(multimap.entries().remove(Map.entry("a", 1))).isTrue();
            assertThat(multimap.entries().remove(Map.entry("a", 2))).isFalse();
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> multimap.entries().iterator().next().setValue(9));
            var entries = multimap.entries().iterator();
            while (entries.hasNext()) {
                entries.next();
                entries.remove();
            }

            assertThat(multimap.isEmpty()).isTrue();
            assertConsistent(multimap);
        }

        @Test
        void showTheMultimapAsAMap() {
            multimap.putAll("a", List.of(1, 2));
            multimap.put("b", 3);
            Map<String, List<Integer>> map = multimap.asMap();

            assertThat(map).hasSize(2);
            assertThat(map.get("a")).containsExactly(1, 2);
            assertThat(map.get("z")).isNull();
            assertThat(map.containsKey("b")).isTrue();
            assertThat(map.remove("b")).containsExactly(3);
            assertThat(map.remove("b")).isNull();
            requireNonNull(map.get("a")).set(0, 9);
            requireNonNull(map.get("a")).remove(Integer.valueOf(2));

            assertThat(multimap.get("a")).containsExactly(9);
            assertConsistent(multimap);
        }

        @Test
        void doNotAddThroughTheMap() {
            multimap.put("a", 1);
            Map<String, List<Integer>> map = multimap.asMap();

            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> map.put("b", List.of(2)));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> requireNonNull(map.get("a")).add(2));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(
                            () -> map.entrySet().iterator().next().getValue().add(2));
            assertThat(multimap.get("a")).containsExactly(1);
        }

        @Test
        void removeThroughTheEntriesOfTheMap() {
            multimap.putAll("a", List.of(1, 2));
            multimap.put("b", 3);

            multimap.asMap().entrySet().removeIf(entry -> entry.getKey().equals("a"));
            multimap.asMap().values().iterator().next().clear();

            assertThat(multimap.isEmpty()).isTrue();
            assertConsistent(multimap);
        }

        @Test
        void clearTheMultimap() {
            multimap.put("a", 1);
            multimap.keySet().clear();
            multimap.put("a", 1);
            multimap.values().clear();
            multimap.put("a", 1);
            multimap.entries().clear();
            multimap.put("a", 1);
            multimap.asMap().clear();

            assertThat(multimap.isEmpty()).isTrue();
        }

        @Test
        void failFastWhenTheMultimapChanges() {
            multimap.put("a", 1);
            multimap.put("b", 2);
            var values = multimap.values().iterator();
            values.next();
            multimap.put("c", 3);

            assertThatExceptionOfType(ConcurrentModificationException.class).isThrownBy(values::next);
        }

        @Test
        void failToRemoveFromValuesThatLeftTheMultimap() {
            multimap.putAll("a", List.of(1, 2));
            var values = multimap.values().iterator();
            values.next();
            multimap.removeAll("a");
            multimap.putAll("a", List.of(1, 2));

            assertThatExceptionOfType(ConcurrentModificationException.class).isThrownBy(values::remove);
            assertThat(multimap.get("a")).containsExactly(1, 2);
            assertConsistent(multimap);
        }
    }
}
