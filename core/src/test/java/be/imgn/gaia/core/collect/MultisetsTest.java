package be.imgn.gaia.core.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MultisetsTest {

    private static final List<String> ORDER = List.of("foo", "foo", "foo", "quuz", "bar", "bar");

    static Stream<Arguments> everyFactory() {
        return Stream.of(
                Arguments.of(Multisets.multiset("foo", "quuz", "foo", "bar", "foo", "bar")),
                Arguments.of(Multisets.multisetCopy(List.of("foo", "quuz", "foo", "bar", "foo", "bar"))),
                Arguments.of(Multisets.multisetCopy(Multisets.multiset("foo", "quuz", "foo", "bar", "foo", "bar"))),
                Arguments.of(
                        Stream.of("foo", "quuz", "foo", "bar", "foo", "bar").collect(Multisets.toMultiset())),
                Arguments.of(Stream.of("foo", "quuz", "foo", "bar", "foo", "bar")
                        .parallel()
                        .collect(Multisets.toMultiset())));
    }

    static Stream<Arguments> everyMutator() {
        Stream<Consumer<Multiset<String>>> mutators = Stream.of(
                multiset -> multiset.add("foo"),
                multiset -> multiset.add("foo", 0),
                multiset -> multiset.remove("quuz"),
                multiset -> multiset.remove("foo", 0),
                multiset -> multiset.setCount("foo", 3),
                multiset -> multiset.addAll(List.of()),
                multiset -> multiset.removeAll(List.of()),
                multiset -> multiset.retainAll(List.of("foo", "quuz", "bar")),
                multiset -> multiset.removeIf(element -> false),
                Multiset::clear,
                multiset -> multiset.elementSet().remove("baz"),
                multiset -> multiset.elementSet().removeIf(element -> false),
                multiset -> multiset.entrySet().clear(),
                multiset -> multiset.entrySet().iterator().next().setCount(3),
                multiset -> {
                    var iterator = multiset.iterator();
                    iterator.next();
                    iterator.remove();
                },
                multiset -> {
                    var iterator = multiset.elementSet().iterator();
                    iterator.next();
                    iterator.remove();
                });
        return mutators.map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("everyFactory")
    void iteratesInTheOrderTheElementsFirstAppear(Multiset<String> multiset) {
        assertThat(multiset).containsExactlyElementsOf(ORDER);
        assertThat(multiset.stream().toList()).isEqualTo(ORDER);
        assertThat(multiset.toArray()).containsExactly(ORDER.toArray());
        assertThat(multiset.elementSet()).containsExactly("foo", "quuz", "bar");
        assertThat(multiset.entrySet()).map(Multiset.Entry::toString).containsExactly("foo=3", "quuz=1", "bar=2");
        assertThat(multiset).hasSize(6);
    }

    @ParameterizedTest
    @MethodSource("everyFactory")
    void equalsAMultisetWithTheSameElementsAndCounts(Multiset<String> multiset) {
        var same = HashMultiset.create(ORDER);

        assertThat(multiset).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(same).isEqualTo(multiset);
        assertThat(multiset).hasToString("[foo × 3, quuz, bar × 2]");
    }

    @ParameterizedTest
    @MethodSource("everyFactory")
    void reportsAnOrderedImmutableSizedSpliterator(Multiset<String> multiset) {
        Spliterator<String> spliterator = multiset.spliterator();

        assertThat(spliterator.characteristics())
                .isEqualTo(Spliterator.ORDERED | Spliterator.IMMUTABLE | Spliterator.SIZED | Spliterator.SUBSIZED);
        assertThat(spliterator.getExactSizeIfKnown()).isEqualTo(6);
    }

    @ParameterizedTest
    @MethodSource("everyFactory")
    void survivesSerializationWithItsOrder(Multiset<String> multiset) throws IOException, ClassNotFoundException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(multiset);
        }

        Object read;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            read = in.readObject();
        }

        assertThat(read).isEqualTo(multiset);
        assertThat(List.<Object>copyOf((Multiset<?>) read)).containsExactlyElementsOf(ORDER);
    }

    @ParameterizedTest
    @MethodSource("everyFactory")
    void rejectsQueriesWithNull(Multiset<String> multiset) {
        assertThatNullPointerException().isThrownBy(() -> multiset.count(null));
        assertThatNullPointerException().isThrownBy(() -> multiset.contains(null));
        assertThatNullPointerException().isThrownBy(() -> multiset.elementSet().contains(null));
    }

    @ParameterizedTest
    @MethodSource("everyMutator")
    void rejectsEveryMutationEvenWithoutAnEffect(Consumer<Multiset<String>> mutator) {
        var multiset = Multisets.multiset("foo", "quuz", "foo", "bar", "foo", "bar");

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> mutator.accept(multiset));
        assertThat(multiset).containsExactlyElementsOf(ORDER);
    }

    @Test
    void createsAnEmptyMultiset() {
        assertThat(Multisets.multiset()).isEmpty();
        assertThat(Multisets.multisetCopy(List.of())).isEmpty();
        assertThat(Stream.<String>empty().collect(Multisets.toMultiset())).isEmpty();
    }

    @Test
    void countsTheOccurrences() {
        var multiset = Multisets.multiset("foo", "quuz", "foo", "bar", "foo", "bar");

        assertThat(multiset.count("foo")).isEqualTo(3);
        assertThat(multiset.count("baz")).isZero();
        assertThat(multiset.contains("quuz")).isTrue();
    }

    @Test
    void copiesTheCountsOfAMultiset() {
        var source = HashMultiset.<String>create();
        source.add("foo", Integer.MAX_VALUE);

        var copy = Multisets.multisetCopy(source);

        assertThat(copy.count("foo")).isEqualTo(Integer.MAX_VALUE);
        assertThat(copy).hasSize(Integer.MAX_VALUE);
    }

    @Test
    void streamsAndShowsMoreOccurrencesThanItsSizeCounts() {
        var source = TreeMultiset.<String>create();
        source.add("foo", Integer.MAX_VALUE);
        source.add("bar", 5);

        var copy = Multisets.multisetCopy(source);

        assertThat(copy.spliterator().getExactSizeIfKnown()).isEqualTo(Integer.MAX_VALUE + 5L);
        assertThat(copy.stream().count()).isEqualTo(Integer.MAX_VALUE + 5L);
        assertThat(copy).hasToString("[bar × 5, foo × 2147483647]");
    }

    @Test
    void rejectsAMultisetThatReportsANullElement() {
        var broken = new FixedEntries(Arrays.asList(null, "foo"), 1);

        assertThatNullPointerException()
                .isThrownBy(() -> Multisets.multisetCopy(broken))
                .withMessage("elements contains null");
    }

    @Test
    void rejectsAMultisetThatReportsACountThatIsNotPositive() {
        var broken = new FixedEntries(List.of("foo"), 0);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Multisets.multisetCopy(broken))
                .withMessage("elements has a count that is not positive: foo=0");
    }

    /** A broken multiset, whose entries are the given elements, each with the given count. */
    private static final class FixedEntries extends AbstractCollection<String> implements Multiset<String> {

        private final List<String> elements;
        private final int count;

        FixedEntries(List<String> elements, int count) {
            this.elements = elements;
            this.count = count;
        }

        @Override
        public void forEachEntry(ObjIntConsumer<? super String> action) {
            elements.forEach(element -> action.accept(element, count));
        }

        @Override
        public Iterator<String> iterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int count(@Nullable Object element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int add(String element, int occurrences) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int remove(@Nullable Object element, int occurrences) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int setCount(String element, int count) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<String> elementSet() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Entry<String>> entrySet() {
            throw new UnsupportedOperationException();
        }
    }
}
