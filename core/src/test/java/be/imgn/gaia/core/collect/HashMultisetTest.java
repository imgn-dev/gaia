package be.imgn.gaia.core.collect;

import static be.imgn.gaia.core.collect.HashListMultimapTest.deserialize;
import static be.imgn.gaia.core.collect.HashListMultimapTest.serialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HashMultisetTest {

    private final HashMultiset<String> multiset = HashMultiset.create(List.of("foo", "bar", "foo", "baz", "foo"));

    @Test
    void countsTheOccurrencesOfEachElement() {
        assertThat(multiset.count("foo")).isEqualTo(3);
        assertThat(multiset.count("bar")).isEqualTo(1);
        assertThat(multiset.count("quuz")).isZero();
        assertThat(multiset).hasSize(5);
        assertThat(multiset.elementSet()).containsExactlyInAnyOrder("foo", "bar", "baz");
    }

    @Test
    void createsAnEmptyMultiset() {
        HashMultiset<String> empty = HashMultiset.create();

        assertThat(empty).isEmpty();
        assertThat(empty.elementSet()).isEmpty();
        assertThat(empty.entrySet()).isEmpty();
    }

    @Test
    void iteratesOverEachOccurrenceWithTheOccurrencesOfAnElementInARow() {
        List<String> occurrences = new ArrayList<>(multiset);

        assertThat(occurrences).containsExactlyInAnyOrder("foo", "foo", "foo", "bar", "baz");
        var first = occurrences.indexOf("foo");
        assertThat(occurrences.subList(first, first + 3)).containsOnly("foo");
        assertThat(multiset.toArray()).hasSize(5);
        assertThat(multiset.stream().filter("foo"::equals).count()).isEqualTo(3);
    }

    @Test
    void queriesWithNull() {
        assertThat(multiset.count(null)).isZero();
        assertThat(multiset.contains(null)).isFalse();
        assertThat(multiset.remove(null)).isFalse();
        assertThat(multiset.elementSet().contains(null)).isFalse();
    }

    @Test
    void callsAnActionWithEachEntry() {
        Map<String, Integer> counts = new LinkedHashMap<>();

        multiset.forEachEntry(counts::put);

        assertThat(counts).containsOnly(Map.entry("foo", 3), Map.entry("bar", 1), Map.entry("baz", 1));
    }

    @Test
    void equalsAMultisetWithTheSameElementsAndCounts() {
        var same = HashMultiset.create(List.of("baz", "foo", "foo", "bar", "foo"));

        assertThat(multiset).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(multiset.hashCode())
                .isEqualTo(("foo".hashCode() ^ 3) + ("bar".hashCode() ^ 1) + ("baz".hashCode() ^ 1));
        assertThat(multiset).isNotEqualTo(HashMultiset.create(List.of("foo", "bar", "baz")));
        assertThat(multiset).isNotEqualTo(HashMultiset.create(List.of("foo", "foo", "foo", "bar", "quuz")));
        assertThat(multiset).isNotEqualTo(List.copyOf(multiset));
    }

    @Test
    void survivesSerialization() throws IOException, ClassNotFoundException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(multiset);
        }

        Object read;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            read = in.readObject();
        }

        assertThat(read).isInstanceOf(HashMultiset.class).isEqualTo(multiset);
        assertThat(((HashMultiset<?>) read).size()).isEqualTo(5);
    }

    @Test
    void rejectsATamperedLengthWithoutAllocatingIt() throws IOException {
        byte[] tampered = TamperedStreams.replacingFirstInt(serialize(multiset), 3, 0x7fff_fff0);

        assertThatIOException().isThrownBy(() -> deserialize(tampered));
    }

    @Nested
    class Adding {

        @Test
        void addsOneOccurrence() {
            assertThat(multiset.add("bar")).isTrue();

            assertThat(multiset.count("bar")).isEqualTo(2);
            assertThat(multiset).hasSize(6);
        }

        @Test
        void addsOccurrencesAndReturnsTheCountBefore() {
            assertThat(multiset.add("bar", 3)).isEqualTo(1);
            assertThat(multiset.add("quuz", 2)).isZero();

            assertThat(multiset.count("bar")).isEqualTo(4);
            assertThat(multiset.count("quuz")).isEqualTo(2);
            assertThat(multiset).hasSize(10);
        }

        @Test
        void addsNoOccurrence() {
            assertThat(multiset.add("quuz", 0)).isZero();

            assertThat(multiset.contains("quuz")).isFalse();
            assertThat(multiset).hasSize(5);
        }

        @Test
        void rejectsANegativeNumberOfOccurrences() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> multiset.add("foo", -1))
                    .withMessage("occurrences must not be negative: -1");
        }

        @Test
        void rejectsACountBeyondTheMaximum() {
            multiset.add("quuz", Integer.MAX_VALUE);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> multiset.add("foo", Integer.MAX_VALUE - 2))
                    .withMessage("Too many occurrences of foo: 3 + 2147483645");
            assertThat(multiset.count("foo")).isEqualTo(3);
        }

        @Test
        void capsTheSizeAtTheMaximum() {
            multiset.add("quuz", Integer.MAX_VALUE);

            assertThat(multiset.size()).isEqualTo(Integer.MAX_VALUE);
            assertThat(multiset.count("quuz")).isEqualTo(Integer.MAX_VALUE);

            multiset.remove("quuz", Integer.MAX_VALUE);

            assertThat(multiset).hasSize(5);
        }

        @Test
        void addsTheOccurrencesOfAnotherMultiset() {
            assertThat(multiset.addAll(HashMultiset.create(List.of("bar", "quuz", "quuz"))))
                    .isTrue();

            assertThat(multiset.count("bar")).isEqualTo(2);
            assertThat(multiset.count("quuz")).isEqualTo(2);
            assertThat(multiset).hasSize(8);
        }

        @Test
        void addsTheElementsOfACollection() {
            assertThat(multiset.addAll(List.of("bar", "bar"))).isTrue();
            assertThat(multiset.addAll(List.of())).isFalse();

            assertThat(multiset.count("bar")).isEqualTo(3);
        }
    }

    @Nested
    class Removing {

        @Test
        void removesOneOccurrence() {
            assertThat(multiset.remove("foo")).isTrue();
            assertThat(multiset.remove("quuz")).isFalse();

            assertThat(multiset.count("foo")).isEqualTo(2);
            assertThat(multiset).hasSize(4);
        }

        @Test
        void removesOccurrencesAndReturnsTheCountBefore() {
            assertThat(multiset.remove("foo", 2)).isEqualTo(3);

            assertThat(multiset.count("foo")).isEqualTo(1);
            assertThat(multiset).hasSize(3);
        }

        @Test
        void removesAsManyOccurrencesAsThereAre() {
            assertThat(multiset.remove("foo", 10)).isEqualTo(3);
            assertThat(multiset.remove("quuz", 10)).isZero();

            assertThat(multiset.contains("foo")).isFalse();
            assertThat(multiset.elementSet()).containsExactlyInAnyOrder("bar", "baz");
            assertThat(multiset).hasSize(2);
        }

        @Test
        void removesNoOccurrence() {
            assertThat(multiset.remove("foo", 0)).isEqualTo(3);

            assertThat(multiset.count("foo")).isEqualTo(3);
        }

        @Test
        void rejectsANegativeNumberOfOccurrences() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> multiset.remove("foo", -1))
                    .withMessage("occurrences must not be negative: -1");
        }

        @Test
        void removesEveryOccurrenceOfTheElementsOfACollection() {
            assertThat(multiset.removeAll(List.of("foo", "quuz"))).isTrue();
            assertThat(multiset.removeAll(List.of("quuz"))).isFalse();

            assertThat(multiset).containsExactlyInAnyOrder("bar", "baz");
        }

        @Test
        void keepsEveryOccurrenceOfTheElementsOfACollection() {
            assertThat(multiset.retainAll(List.of("foo", "bar"))).isTrue();
            assertThat(multiset.retainAll(List.of("foo", "bar"))).isFalse();

            assertThat(multiset).containsExactlyInAnyOrder("foo", "foo", "foo", "bar");
        }

        @Test
        void removesEveryOccurrenceOfTheElementsThatMatch() {
            assertThat(multiset.removeIf(element -> element.startsWith("ba"))).isTrue();
            assertThat(multiset.removeIf(element -> element.startsWith("ba"))).isFalse();

            assertThat(multiset).containsExactly("foo", "foo", "foo");
        }

        @Test
        void clears() {
            multiset.clear();

            assertThat(multiset).isEmpty();
            assertThat(multiset.count("foo")).isZero();
        }

        @Test
        void removesOneOccurrenceThroughTheIterator() {
            for (var iterator = multiset.iterator(); iterator.hasNext(); ) {
                if (iterator.next().equals("foo")) {
                    iterator.remove();
                }
            }

            assertThat(multiset).containsExactlyInAnyOrder("bar", "baz");
        }

        @Test
        void removesOnlyTheOccurrenceJustReturnedThroughTheIterator() {
            var iterator = multiset.iterator();
            var element = iterator.next();
            iterator.remove();

            assertThatIllegalStateException().isThrownBy(iterator::remove);
            assertThat(multiset).hasSize(4);
            assertThat(multiset.count(element)).isEqualTo(element.equals("foo") ? 2 : 0);
        }

        @Test
        void throwsWhenTheIteratorHasNoMoreOccurrences() {
            var iterator = multiset.iterator();
            for (var i = 0; i < 5; i++) {
                iterator.next();
            }

            assertThat(iterator.hasNext()).isFalse();
            assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(iterator::next);
        }

        @Test
        void failsTheIteratorWhenAnElementIsAddedMeanwhile() {
            var iterator = multiset.iterator();
            iterator.next();
            multiset.add("quuz");

            assertThatExceptionOfType(ConcurrentModificationException.class).isThrownBy(() -> {
                while (iterator.hasNext()) {
                    iterator.next();
                }
            });
        }
    }

    @Nested
    class SettingTheCount {

        @Test
        void setsTheCountAndReturnsTheCountBefore() {
            assertThat(multiset.setCount("foo", 1)).isEqualTo(3);
            assertThat(multiset.setCount("quuz", 4)).isZero();

            assertThat(multiset.count("foo")).isEqualTo(1);
            assertThat(multiset.count("quuz")).isEqualTo(4);
            assertThat(multiset).hasSize(7);
        }

        @Test
        void removesTheElementWithACountOfZero() {
            assertThat(multiset.setCount("foo", 0)).isEqualTo(3);
            assertThat(multiset.setCount("quuz", 0)).isZero();

            assertThat(multiset.elementSet()).containsExactlyInAnyOrder("bar", "baz");
            assertThat(multiset).hasSize(2);
        }

        @Test
        void rejectsANegativeCount() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> multiset.setCount("foo", -1))
                    .withMessage("count must not be negative: -1");
        }
    }

    @Nested
    class ElementSet {

        @Test
        void followsTheMultiset() {
            Set<String> elements = multiset.elementSet();

            multiset.add("quuz");
            multiset.remove("bar");

            assertThat(elements).containsExactlyInAnyOrder("foo", "baz", "quuz");
            assertThat(elements).hasSize(3);
        }

        @Test
        void removesEveryOccurrenceOfAnElement() {
            assertThat(multiset.elementSet().remove("foo")).isTrue();
            assertThat(multiset.elementSet().remove("quuz")).isFalse();

            assertThat(multiset).containsExactlyInAnyOrder("bar", "baz");
        }

        @Test
        void removesEveryOccurrenceThroughTheIterator() {
            multiset.elementSet().removeIf("foo"::equals);

            assertThat(multiset).containsExactlyInAnyOrder("bar", "baz");
            assertThat(multiset).hasSize(2);
        }

        @Test
        void clearsTheMultiset() {
            multiset.elementSet().clear();

            assertThat(multiset).isEmpty();
        }

        @Test
        void doesNotAdd() {
            Set<String> elements = multiset.elementSet();

            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> elements.add("quuz"));
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> elements.addAll(List.of()));
        }
    }

    @Nested
    class EntrySet {

        @Test
        void hasAnEntryPerElementWithItsCount() {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (var entry : multiset.entrySet()) {
                counts.put(entry.element(), entry.count());
            }

            assertThat(counts).containsOnly(Map.entry("foo", 3), Map.entry("bar", 1), Map.entry("baz", 1));
            assertThat(multiset.entrySet()).hasSize(3);
        }

        @Test
        void hasLiveEntries() {
            var entry = entry("foo");

            multiset.add("foo");
            assertThat(entry.count()).isEqualTo(4);

            multiset.remove("foo", 4);
            assertThat(entry.count()).isZero();
        }

        @Test
        void setsTheCountThroughAnEntry() {
            var entry = entry("foo");

            assertThat(entry.setCount(5)).isEqualTo(3);

            assertThat(multiset.count("foo")).isEqualTo(5);
            assertThat(multiset).hasSize(7);
        }

        @Test
        void setsTheCountThroughEachEntryWhileIterating() {
            for (var entry : multiset.entrySet()) {
                entry.setCount(entry.count() * 2);
            }

            assertThat(multiset).hasSize(10);
            assertThat(multiset.count("foo")).isEqualTo(6);
        }

        @Test
        void rejectsACountThatIsNotPositiveThroughAnEntry() {
            var entry = entry("foo");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> entry.setCount(0))
                    .withMessage("count must be positive: 0");
        }

        @Test
        void rejectsSettingTheCountOfAnElementNoLongerThere() {
            var entry = entry("foo");
            multiset.elementSet().remove("foo");

            assertThatIllegalStateException()
                    .isThrownBy(() -> entry.setCount(1))
                    .withMessage("foo is no longer in the multiset");
            assertThat(multiset.contains("foo")).isFalse();
        }

        @Test
        void removesEveryOccurrenceThroughTheIterator() {
            multiset.entrySet().removeIf(entry -> entry.count() > 1);

            assertThat(multiset).containsExactlyInAnyOrder("bar", "baz");
        }

        @Test
        void containsAndRemovesEntriesWithTheRightCountOnly() {
            Set<Multiset.Entry<String>> entries = multiset.entrySet();
            var foo = HashMultiset.create(List.of("foo", "foo", "foo"))
                    .entrySet()
                    .iterator()
                    .next();
            var fewerFoo =
                    HashMultiset.create(List.of("foo")).entrySet().iterator().next();

            assertThat(entries).contains(foo).doesNotContain(fewerFoo);
            assertThat(entries.remove(fewerFoo)).isFalse();
            assertThat(entries.remove(foo)).isTrue();
            assertThat(multiset.contains("foo")).isFalse();
        }

        @Test
        void comparesEntriesByElementAndCount() {
            var entry = entry("foo");
            var same = HashMultiset.create(List.of("foo", "foo", "foo"))
                    .entrySet()
                    .iterator()
                    .next();

            assertThat(entry).isEqualTo(same).hasSameHashCodeAs(same).hasToString("foo=3");
            assertThat(entry.hashCode()).isEqualTo("foo".hashCode() ^ 3);
            assertThat(entry).isNotEqualTo(entry("bar"));
        }

        private Multiset.Entry<String> entry(String element) {
            return multiset.entrySet().stream()
                    .filter(entry -> entry.element().equals(element))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
