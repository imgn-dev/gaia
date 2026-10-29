package be.imgn.gaia.core.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SetsTest {

    static Stream<Arguments> everyFactory() {
        return Stream.of(
                Arguments.of(Sets.set(), List.of()),
                Arguments.of(Sets.set(1), List.of(1)),
                Arguments.of(Sets.set(2, 1), List.of(2, 1)),
                Arguments.of(Sets.set(3, 1, 2), List.of(3, 1, 2)),
                Arguments.of(Sets.set(4, 3, 2, 1), List.of(4, 3, 2, 1)),
                Arguments.of(Sets.set(5, 1, 4, 2, 3), List.of(5, 1, 4, 2, 3)),
                Arguments.of(Sets.set(6, 5, 4, 3, 2, 1), List.of(6, 5, 4, 3, 2, 1)),
                Arguments.of(Sets.set(7, 1, 6, 2, 5, 3, 4), List.of(7, 1, 6, 2, 5, 3, 4)),
                Arguments.of(Sets.set(8, 7, 6, 5, 4, 3, 2, 1), List.of(8, 7, 6, 5, 4, 3, 2, 1)),
                Arguments.of(Sets.set(9, 1, 8, 2, 7, 3, 6, 4, 5), List.of(9, 1, 8, 2, 7, 3, 6, 4, 5)),
                Arguments.of(Sets.set(10, 9, 8, 7, 6, 5, 4, 3, 2, 1), List.of(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)),
                Arguments.of(Sets.set(11, 1, 10, 2, 9, 3, 8, 4, 7, 5, 6), List.of(11, 1, 10, 2, 9, 3, 8, 4, 7, 5, 6)),
                Arguments.of(Sets.set(new Integer[] {3, 2, 1}), List.of(3, 2, 1)),
                Arguments.of(Sets.setCopy(List.of(3, 1, 3, 2, 1)), List.of(3, 1, 2)));
    }

    @ParameterizedTest
    @MethodSource("everyFactory")
    void iteratesInTheGivenOrder(Set<Integer> set, List<Integer> order) {
        assertThat(set).containsExactlyElementsOf(order);
        assertThat(set.stream().toList()).isEqualTo(order);
        assertThat(set.toArray()).containsExactly(order.toArray());
        assertThat(set.toArray(new Integer[0])).containsExactly(order.toArray(new Integer[0]));
    }

    @ParameterizedTest
    @MethodSource("everyFactory")
    void equalsTheSetOfWithTheSameElements(Set<Integer> set, List<Integer> order) {
        Set<Integer> jdk = Set.copyOf(order);

        assertThat(set).isEqualTo(jdk);
        assertThat(jdk).isEqualTo(set);
        assertThat(set).hasSameHashCodeAs(jdk);
        assertThat(set).hasToString(order.toString());
    }

    @ParameterizedTest
    @MethodSource("everyFactory")
    void reportsTheSpliteratorCharacteristicsOfSetOfPlusOrdered(Set<Integer> set, List<Integer> order) {
        Spliterator<Integer> spliterator = set.spliterator();

        assertThat(spliterator.characteristics())
                .isEqualTo(Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.SIZED | Spliterator.SUBSIZED);
        assertThat(spliterator.getExactSizeIfKnown()).isEqualTo(order.size());
    }

    @ParameterizedTest
    @MethodSource("everyFactory")
    void survivesSerializationWithItsOrder(Set<Integer> set, List<Integer> order)
            throws IOException, ClassNotFoundException {
        Object copy = deserialize(serialize(set));

        assertThat(copy).isInstanceOf(Set.class).isEqualTo(set);
        assertThat(List.copyOf((Set<?>) copy)).isEqualTo(order);
    }

    @Test
    void rejectsATamperedLengthWithoutAllocatingIt() throws IOException {
        byte[] tampered = TamperedStreams.replacingFirstInt(serialize(Sets.set(3, 1, 2)), 3, 0x7fff_fff0);

        assertThatIOException().isThrownBy(() -> deserialize(tampered));
    }

    @Test
    void rejectsATamperedDuplicateAsAnInvalidStream() throws IOException {
        byte[] tampered = TamperedStreams.replacingFirstString(serialize(Sets.set("x", "y")), "y", "x");

        assertThatExceptionOfType(InvalidObjectException.class).isThrownBy(() -> deserialize(tampered));
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
    class LikeSetOf {

        @Test
        void rejectsDuplicates() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Sets.set("a", "b", "a"))
                    .withMessage("duplicate element: a");
            assertThatIllegalArgumentException().isThrownBy(() -> Sets.set(new String[] {"a", "a"}));
        }

        // Passing null is the point.
        @Test
        @SuppressWarnings("NullAway")
        void rejectsNullElements() {
            assertThatNullPointerException().isThrownBy(() -> Sets.set((String) null));
            assertThatNullPointerException().isThrownBy(() -> Sets.set("a", null));
            assertThatNullPointerException().isThrownBy(() -> Sets.set((String[]) null));
        }

        // Passing null is the point.
        @Test
        @SuppressWarnings("NullAway")
        void throwsWhenQueriedWithNull() {
            Set<String> set = Sets.set("a", "b");

            assertThatNullPointerException().isThrownBy(() -> set.contains(null));
            assertThatNullPointerException().isThrownBy(() -> set.containsAll(Arrays.asList("a", null)));
        }

        @Test
        void isNotEqualToASetContainingNull() {
            assertThat(Sets.set("a", "b")).isNotEqualTo(new HashSet<>(Arrays.asList("a", null)));
        }
    }

    @Nested
    class LikeSetCopyOf {

        @Test
        void keepsTheFirstOccurrenceOfEachElement() {
            assertThat(Sets.setCopy(List.of("b", "a", "b", "c", "a"))).containsExactly("b", "a", "c");
        }

        @Test
        void doesNotFollowLaterChangesToTheSource() {
            List<String> source = new ArrayList<>(List.of("b", "a"));
            Set<String> copy = Sets.setCopy(source);

            source.add("c");

            assertThat(copy).containsExactly("b", "a");
        }

        @Test
        void returnsAnOrderedSetAsIs() {
            Set<String> set = Sets.set("b", "a");

            assertThat(Sets.setCopy(set)).isSameAs(set);
        }

        // Passing null is the point.
        @Test
        @SuppressWarnings("NullAway")
        void rejectsNull() {
            assertThatNullPointerException().isThrownBy(() -> Sets.setCopy((List<String>) null));
            assertThatNullPointerException().isThrownBy(() -> Sets.setCopy(Arrays.asList("a", null)));
        }

        @Test
        void isDistinctFromSetWithACollectionElement() {
            List<String> list = List.of("b", "a");

            assertThat(Sets.setCopy(list)).containsExactly("b", "a");
            assertThat(Sets.set(list)).containsExactly(list);
        }
    }

    @Nested
    class Collecting {

        @Test
        void keepsTheEncounterOrderAndTheFirstOccurrence() {
            assertThat(Stream.of("c", "a", "c", "b", "a").collect(Sets.toSet())).containsExactly("c", "a", "b");
        }

        @Test
        void keepsTheEncounterOrderOfAParallelStream() {
            List<Integer> numbers = IntStream.rangeClosed(1, 10_000)
                    .map(i -> 10_001 - i)
                    .boxed()
                    .toList();

            assertThat(numbers.parallelStream().collect(Sets.toSet())).containsExactlyElementsOf(numbers);
        }

        @Test
        void collectsAnOrderedUnmodifiableSet() {
            Set<String> set = Stream.of("b", "a").collect(Sets.toSet());

            assertThat(Sets.setCopy(set)).isSameAs(set);
            assertThat(set).isEqualTo(Set.of("a", "b"));
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> set.add("c"));
        }

        @Test
        void collectsAnEmptyStream() {
            assertThat(Stream.<String>empty().collect(Sets.toSet())).isEmpty();
        }

        @Test
        void rejectsNullElements() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Stream.of("a", null, "b").collect(Sets.toSet()));
        }
    }

    @Nested
    class Unmodifiable {

        static Stream<Arguments> mutators() {
            return Stream.<Consumer<Set<String>>>of(
                            set -> set.add("c"),
                            set -> set.add("a"),
                            set -> set.addAll(List.of()),
                            set -> set.remove("a"),
                            set -> set.remove("z"),
                            set -> set.removeAll(List.of()),
                            set -> set.retainAll(List.of("a", "b")),
                            set -> set.removeIf(element -> false),
                            Set::clear,
                            set -> {
                                var iterator = set.iterator();
                                iterator.next();
                                iterator.remove();
                            })
                    .map(Arguments::of);
        }

        @ParameterizedTest
        @MethodSource("mutators")
        void throwsOnEveryMutationEvenANoOp(Consumer<Set<String>> mutation) {
            Set<String> set = Sets.set("a", "b");

            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> mutation.accept(set));
            assertThat(set).containsExactly("a", "b");
        }

        @Test
        void throwsEvenWhenEmpty() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> Sets.set().clear());
        }
    }
}
