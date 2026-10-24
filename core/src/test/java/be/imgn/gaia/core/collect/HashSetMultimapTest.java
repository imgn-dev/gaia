package be.imgn.gaia.core.collect;

import static be.imgn.gaia.core.collect.HashListMultimapTest.assertConsistent;
import static be.imgn.gaia.core.collect.HashListMultimapTest.deserialize;
import static be.imgn.gaia.core.collect.HashListMultimapTest.serialize;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HashSetMultimapTest {

    private final HashSetMultimap<String, Integer> multimap = HashSetMultimap.create();

    @Test
    void ignoresDuplicateEntries() {
        assertThat(multimap.put("a", 1)).isTrue();
        assertThat(multimap.put("a", 1)).isFalse();
        assertThat(multimap.putAll("a", List.of(1, 2, 2))).isTrue();
        assertThat(multimap.putAll("a", List.of(1, 2))).isFalse();

        assertThat(multimap.get("a")).containsExactlyInAnyOrder(1, 2);
        assertThat(multimap.size()).isEqualTo(2);
        assertConsistent(multimap);
    }

    @Test
    void addsThroughTheValuesOfAKey() {
        Set<Integer> values = multimap.get("a");

        assertThat(values.add(1)).isTrue();
        assertThat(values.add(1)).isFalse();
        assertThat(values.addAll(List.of(1, 2))).isTrue();

        assertThat(multimap.get("a")).containsExactlyInAnyOrder(1, 2);
        assertConsistent(multimap);
    }

    @Test
    void removesThroughTheValuesOfAKeyAndThenTheKey() {
        multimap.putAll("a", List.of(1, 2, 3));
        Set<Integer> values = multimap.get("a");

        assertThat(values.remove(1)).isTrue();
        assertThat(values.remove(1)).isFalse();
        var iterator = values.iterator();
        iterator.next();
        iterator.remove();
        values.removeIf(value -> true);

        assertThat(multimap.containsKey("a")).isFalse();
        assertConsistent(multimap);
    }

    @Test
    void replacesValuesWithoutDuplicates() {
        multimap.put("a", 1);

        Set<Integer> replaced = multimap.replaceValues("a", List.of(2, 2, 3));

        assertThat(replaced).containsExactly(1);
        assertThat(multimap.get("a")).containsExactlyInAnyOrder(2, 3);
        assertThat(multimap.size()).isEqualTo(2);
        assertConsistent(multimap);
    }

    @Test
    void hasDistinctEntries() {
        multimap.putAll("a", List.of(1, 2));
        multimap.put("b", 1);
        Set<Map.Entry<String, Integer>> entries = multimap.entries();

        assertThat(entries).isEqualTo(Set.of(Map.entry("a", 1), Map.entry("a", 2), Map.entry("b", 1)));
        assertThat(entries).hasSameHashCodeAs(Set.of(Map.entry("a", 1), Map.entry("a", 2), Map.entry("b", 1)));
        assertThat(entries.remove(Map.entry("a", 1))).isTrue();
        assertThat(multimap.get("a")).containsExactly(2);
        assertConsistent(multimap);
    }

    @Test
    void equalsAMultimapWithTheSameValuesInAnyOrder() {
        multimap.putAll("a", List.of(1, 2));
        var other = HashSetMultimap.<String, Integer>create();
        other.putAll("a", List.of(2, 1));
        var list = HashListMultimap.<String, Integer>create();
        list.putAll("a", List.of(1, 2));

        assertThat(multimap).isEqualTo(other).hasSameHashCodeAs(other);
        assertThat(multimap.asMap()).isEqualTo(Map.of("a", Set.of(1, 2)));
        assertThat(multimap).isNotEqualTo(list);
        assertThat(HashSetMultimap.create()).isEqualTo(HashListMultimap.create());
    }

    @Test
    void failsToRemoveFromValuesThatLeftTheMultimap() {
        multimap.putAll("a", List.of(1, 2));
        var values = multimap.get("a").iterator();
        values.next();
        multimap.removeAll("a");
        multimap.putAll("a", List.of(1, 2));

        assertThatExceptionOfType(ConcurrentModificationException.class).isThrownBy(values::remove);
        assertThat(multimap.get("a")).containsExactlyInAnyOrder(1, 2);
        assertConsistent(multimap);
    }

    @Test
    void doesNotAddThroughTheMap() {
        multimap.put("a", 1);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> requireNonNull(multimap.asMap().get("a")).add(2));
        assertThat(requireNonNull(multimap.asMap().get("a")).remove(1)).isTrue();
        assertThat(multimap.isEmpty()).isTrue();
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsANullMultimapToCopy() {
        assertThatNullPointerException()
                .isThrownBy(() -> HashSetMultimap.create(null))
                .withMessage("multimap");
    }

    @Test
    void survivesSerialization() throws IOException, ClassNotFoundException {
        multimap.putAll("a", List.of(1, 2));
        multimap.put("b", 3);

        Object copy = deserialize(serialize(multimap));

        assertThat(copy).isInstanceOf(HashSetMultimap.class).isEqualTo(multimap);
        assertConsistent((Multimap<?, ?>) copy);
    }
}
