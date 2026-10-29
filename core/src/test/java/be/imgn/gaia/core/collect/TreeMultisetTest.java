package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TreeMultisetTest {

    private final TreeMultiset<String> multiset =
            TreeMultiset.create(List.of("foo", "quuz", "bar", "foo", "baz", "foo", "quuz"));

    @Test
    void iteratesInAscendingOrder() {
        assertThat(multiset).containsExactly("bar", "baz", "foo", "foo", "foo", "quuz", "quuz");
        assertThat(multiset.elementSet()).containsExactly("bar", "baz", "foo", "quuz");
        assertThat(multiset.entrySet().stream().map(Multiset.Entry::count)).containsExactly(1, 1, 3, 2);
        assertThat(multiset).hasSize(7);
    }

    @Test
    void streamsAndShowsMoreOccurrencesThanItsSizeCounts() {
        var many = TreeMultiset.<String>create();
        many.add("foo", Integer.MAX_VALUE);
        many.add("bar", 5);

        assertThat(many).hasSize(Integer.MAX_VALUE);
        assertThat(many.spliterator().getExactSizeIfKnown()).isEqualTo(Integer.MAX_VALUE + 5L);
        assertThat(many.stream().count()).isEqualTo(Integer.MAX_VALUE + 5L);
        assertThat(many).hasToString("[bar × 5, foo × 2147483647]");
    }

    @Test
    void sortsByTheNaturalOrdering() {
        assertThat(multiset.comparator()).isNull();
        assertThat(TreeMultiset.<String>create()).isEmpty();
    }

    @Test
    void sortsByAComparator() {
        TreeMultiset<String> byLength = TreeMultiset.create(Comparator.comparingInt(String::length));
        byLength.addAll(List.of("quuz", "foo", "a"));

        assertThat(byLength.comparator()).isNotNull();
        assertThat(byLength).containsExactly("a", "foo", "quuz");
        assertThat(byLength.add("bar")).isTrue();
        assertThat(byLength.count("foo")).isEqualTo(2);
    }

    @Test
    void rejectsQueriesWithNullUnderTheNaturalOrdering() {
        assertThatNullPointerException().isThrownBy(() -> multiset.count(null));
    }

    @Test
    void equalsAHashMultisetWithTheSameElementsAndCounts() {
        var same = HashMultiset.create(multiset);

        assertThat(multiset).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(same).isEqualTo(multiset);
    }

    @Test
    void doesNotEqualAMultisetOfElementsItCannotCompare() {
        var numbers = HashMultiset.create(List.of(1, 2, 3, 4));

        assertThat(numbers.elementSet()).hasSameSizeAs(multiset.elementSet());
        assertThat(numbers).isNotEqualTo(multiset);
    }

    @Test
    void survivesSerializationWithItsComparator() throws IOException, ClassNotFoundException {
        TreeMultiset<String> ignoringCase = TreeMultiset.create(String.CASE_INSENSITIVE_ORDER);
        ignoringCase.addAll(List.of("Foo", "bar", "bar"));

        var read = (TreeMultiset<?>) serializeAndDeserialize(ignoringCase);

        assertThat(read).isEqualTo(ignoringCase);
        assertThat(List.<Object>copyOf(read)).containsExactly("bar", "bar", "Foo");
        assertThat(read.comparator()).isEqualTo(String.CASE_INSENSITIVE_ORDER);
        assertThat(read.count("FOO")).isEqualTo(1);
    }

    @Test
    void serializesAViewAsACopy() throws IOException, ClassNotFoundException {
        var view = multiset.headMultiset("foo", true).reversed();

        var read = (TreeMultiset<?>) serializeAndDeserialize(view);

        assertThat(read).isEqualTo(view);
        assertThat(List.<Object>copyOf(read)).containsExactly("foo", "foo", "foo", "baz", "bar");
        multiset.clear();
        assertThat(read).hasSize(5);
    }

    private static Object serializeAndDeserialize(Object object) throws IOException, ClassNotFoundException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(object);
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return in.readObject();
        }
    }

    @Nested
    class Entries {

        @Test
        void returnsTheFirstAndLastEntries() {
            assertThat(multiset.firstEntry())
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("bar=1"));
            assertThat(multiset.lastEntry())
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("quuz=2"));
            assertThat(TreeMultiset.<String>create().firstEntry()).isEmpty();
            assertThat(TreeMultiset.<String>create().lastEntry()).isEmpty();
        }

        @Test
        void returnsLiveFirstAndLastEntries() {
            var last = multiset.lastEntry().orElseThrow();

            last.setCount(5);
            multiset.add("quuz");

            assertThat(multiset.count("quuz")).isEqualTo(6);
            assertThat(last.count()).isEqualTo(6);
        }

        @Test
        void returnsTheNearestEntries() {
            assertThat(multiset.lowerEntry("foo"))
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("baz=1"));
            assertThat(multiset.floorEntry("foo"))
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("foo=3"));
            assertThat(multiset.ceilingEntry("foo"))
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("foo=3"));
            assertThat(multiset.higherEntry("foo"))
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("quuz=2"));
            assertThat(multiset.floorEntry("bat"))
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("bar=1"));
            assertThat(multiset.ceilingEntry("bat"))
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("baz=1"));
        }

        @Test
        void returnsNoNearestEntryBeyondTheEnds() {
            assertThat(multiset.lowerEntry("bar")).isEmpty();
            assertThat(multiset.floorEntry("a")).isEmpty();
            assertThat(multiset.ceilingEntry("z")).isEmpty();
            assertThat(multiset.higherEntry("quuz")).isEmpty();
        }

        @Test
        void returnsLiveNearestEntries() {
            var floor = multiset.floorEntry("foo").orElseThrow();

            floor.setCount(5);
            multiset.add("foo");

            assertThat(multiset.count("foo")).isEqualTo(6);
            assertThat(floor.count()).isEqualTo(6);
        }

        @Test
        void pollsTheFirstAndLastEntries() {
            var first = multiset.pollFirstEntry().orElseThrow();
            var last = multiset.pollLastEntry().orElseThrow();

            assertThat(first).hasToString("bar=1");
            assertThat(last).hasToString("quuz=2");
            assertThat(multiset).containsExactly("baz", "foo", "foo", "foo");
            assertThat(multiset).hasSize(4);
        }

        @Test
        void returnsPolledEntriesThatNoLongerChange() {
            var last = multiset.pollLastEntry().orElseThrow();
            multiset.add("quuz");

            assertThat(last.count()).isEqualTo(2);
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> last.setCount(1));
            assertThat(last)
                    .isEqualTo(TreeMultiset.create(List.of("quuz", "quuz"))
                            .firstEntry()
                            .orElseThrow());
            assertThat(last.hashCode()).isEqualTo("quuz".hashCode() ^ 2);
        }

        @Test
        void pollsNothingFromAnEmptyMultiset() {
            assertThat(TreeMultiset.<String>create().pollFirstEntry()).isEmpty();
            assertThat(TreeMultiset.<String>create().pollLastEntry()).isEmpty();
        }
    }

    @Nested
    class Sequenced {

        @Test
        void returnsTheLowestAndHighestElements() {
            assertThat(multiset.getFirst()).isEqualTo("bar");
            assertThat(multiset.getLast()).isEqualTo("quuz");
            assertThatExceptionOfType(NoSuchElementException.class)
                    .isThrownBy(() -> TreeMultiset.<String>create().getFirst());
        }

        @Test
        void removesOneOccurrenceOfTheLowestAndHighestElements() {
            assertThat(multiset.removeFirst()).isEqualTo("bar");
            assertThat(multiset.removeLast()).isEqualTo("quuz");

            assertThat(multiset).containsExactly("baz", "foo", "foo", "foo", "quuz");
        }

        @Test
        void doesNotAddFirstOrLast() {
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> multiset.addFirst("foo"));
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> multiset.addLast("foo"));
        }
    }

    @Nested
    class Views {

        @Test
        void viewsTheElementsUpToAnElement() {
            assertThat(multiset.headMultiset("foo", false)).containsExactly("bar", "baz");
            assertThat(multiset.headMultiset("foo", true)).containsExactly("bar", "baz", "foo", "foo", "foo");
        }

        @Test
        void viewsTheElementsFromAnElement() {
            assertThat(multiset.tailMultiset("foo", false)).containsExactly("quuz", "quuz");
            assertThat(multiset.tailMultiset("foo", true)).containsExactly("foo", "foo", "foo", "quuz", "quuz");
        }

        @Test
        void viewsTheElementsBetweenTwoElements() {
            var view = multiset.subMultiset("baz", true, "quuz", false);

            assertThat(view).containsExactly("baz", "foo", "foo", "foo").hasSize(4);
            assertThat(view.count("foo")).isEqualTo(3);
            assertThat(view.count("quuz")).isZero();
            assertThat(view.elementSet()).containsExactly("baz", "foo");
            assertThat(view.getFirst()).isEqualTo("baz");
        }

        @Test
        void rejectsBoundsInTheWrongOrder() {
            assertThatIllegalArgumentException().isThrownBy(() -> multiset.subMultiset("quuz", true, "bar", true));
        }

        @Test
        void writesThroughToTheMultiset() {
            var view = multiset.headMultiset("foo", true);

            view.add("bar", 2);
            view.remove("foo");
            view.elementSet().remove("baz");

            assertThat(multiset).containsExactly("bar", "bar", "bar", "foo", "foo", "quuz", "quuz");
            assertThat(multiset).hasSize(7);
            assertThat(view).hasSize(5);
        }

        @Test
        void followsTheMultiset() {
            var view = multiset.tailMultiset("foo", true);

            multiset.add("zzz");
            multiset.add("a");

            assertThat(view).containsExactly("foo", "foo", "foo", "quuz", "quuz", "zzz");
        }

        @Test
        void clearsItsRangeOnly() {
            multiset.subMultiset("baz", true, "foo", true).clear();

            assertThat(multiset).containsExactly("bar", "quuz", "quuz");
            assertThat(multiset).hasSize(3);
        }

        @Test
        void rejectsElementsOutsideItsRange() {
            var view = multiset.headMultiset("foo", false);

            assertThatIllegalArgumentException().isThrownBy(() -> view.add("quuz"));
            assertThatIllegalArgumentException().isThrownBy(() -> view.headMultiset("quuz", true));
            assertThat(view.count("quuz")).isZero();
        }

        @Test
        void pollsTheEntriesOfItsRange() {
            var view = multiset.headMultiset("foo", true);

            assertThat(view.pollLastEntry())
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("foo=3"));
            assertThat(multiset).containsExactly("bar", "baz", "quuz", "quuz");
        }

        @Test
        void returnsTheNearestEntriesOfItsRange() {
            var view = multiset.subMultiset("baz", true, "foo", true);

            assertThat(view.lowerEntry("baz")).isEmpty();
            assertThat(view.higherEntry("foo")).isEmpty();
            assertThat(view.ceilingEntry("bar"))
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("baz=1"));
            assertThat(view.floorEntry("quuz"))
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("foo=3"));
        }

        @Test
        void returnsTheNearestEntriesInReverse() {
            var reversed = multiset.reversed();

            assertThat(reversed.lowerEntry("foo"))
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("quuz=2"));
            assertThat(reversed.higherEntry("foo"))
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("baz=1"));
            assertThat(reversed.floorEntry("bat"))
                    .hasValueSatisfying(entry -> assertThat(entry).hasToString("baz=1"));
        }

        @Test
        void reversesTheOrder() {
            var reversed = multiset.reversed();

            assertThat(reversed).containsExactly("quuz", "quuz", "foo", "foo", "foo", "baz", "bar");
            assertThat(reversed).hasSize(7);
            assertThat(reversed.elementSet()).containsExactly("quuz", "foo", "baz", "bar");
            assertThat(reversed.getFirst()).isEqualTo("quuz");
            assertThat(reversed.headMultiset("foo", true)).containsExactly("quuz", "quuz", "foo", "foo", "foo");
            assertThat(reversed.reversed()).containsExactlyElementsOf(multiset);
            assertThat(requireNonNull(reversed.comparator()).compare("bar", "foo"))
                    .isPositive();
        }

        @Test
        void writesThroughTheReversedView() {
            multiset.reversed().add("zzz", 2);
            multiset.reversed().removeFirst();

            assertThat(multiset).containsExactly("bar", "baz", "foo", "foo", "foo", "quuz", "quuz", "zzz");
            assertThat(multiset.reversed()).hasSize(8);
        }
    }

    @Nested
    class ElementSet {

        private final NavigableSet<String> elements = multiset.elementSet();

        @Test
        void navigates() {
            assertThat(elements.first()).isEqualTo("bar");
            assertThat(elements.last()).isEqualTo("quuz");
            assertThat(elements.lower("baz")).isEqualTo("bar");
            assertThat(elements.lower("bar")).isNull();
            assertThat(elements.floor("bat")).isEqualTo("bar");
            assertThat(elements.ceiling("bat")).isEqualTo("baz");
            assertThat(elements.higher("foo")).isEqualTo("quuz");
            assertThat(elements.higher("quuz")).isNull();
            assertThat(elements.comparator()).isNull();
        }

        @Test
        void viewsRanges() {
            assertThat(elements.headSet("foo")).containsExactly("bar", "baz");
            assertThat(elements.headSet("foo", true)).containsExactly("bar", "baz", "foo");
            assertThat(elements.tailSet("foo")).containsExactly("foo", "quuz");
            assertThat(elements.tailSet("foo", false)).containsExactly("quuz");
            assertThat(elements.subSet("baz", "quuz")).containsExactly("baz", "foo");
            assertThat(elements.subSet("baz", false, "quuz", true)).containsExactly("foo", "quuz");
            assertThat(elements.descendingSet()).containsExactly("quuz", "foo", "baz", "bar");
            assertThat(elements.descendingIterator()).toIterable().containsExactly("quuz", "foo", "baz", "bar");
        }

        @Test
        void pollsElementsWithAllTheirOccurrences() {
            assertThat(elements.pollFirst()).isEqualTo("bar");
            assertThat(elements.pollLast()).isEqualTo("quuz");

            assertThat(multiset).containsExactly("baz", "foo", "foo", "foo");
            assertThat(TreeMultiset.<String>create().elementSet().pollFirst()).isNull();
        }

        @Test
        void removesARangeWithAllItsOccurrences() {
            elements.tailSet("foo").clear();

            assertThat(multiset).containsExactly("bar", "baz");
            assertThat(multiset).hasSize(2);
        }
    }
}
