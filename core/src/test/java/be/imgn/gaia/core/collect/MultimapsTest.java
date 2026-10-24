package be.imgn.gaia.core.collect;

import static be.imgn.gaia.core.collect.HashListMultimapTest.assertConsistent;
import static be.imgn.gaia.core.collect.HashListMultimapTest.deserialize;
import static be.imgn.gaia.core.collect.HashListMultimapTest.serialize;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MultimapsTest {

    private static final List<String> WORDS = List.of("bob", "alice", "bill", "amy", "bob", "carol");

    private static ListMultimap<Character, String> byInitial() {
        var multimap = HashListMultimap.<Character, String>create();
        WORDS.forEach(word -> multimap.put(word.charAt(0), word));
        return multimap;
    }

    static Stream<Arguments> everyListFactory() {
        return Stream.of(
                Arguments.of(WORDS.stream().collect(Multimaps.toListMultimap(word -> word.charAt(0), word -> word))),
                Arguments.of(
                        WORDS.parallelStream().collect(Multimaps.toListMultimap(word -> word.charAt(0), word -> word))),
                Arguments.of(Multimaps.listMultimapCopy(
                        WORDS.stream().collect(Multimaps.toListMultimap(word -> word.charAt(0), word -> word)))));
    }

    static Stream<Arguments> everySetFactory() {
        return Stream.of(
                Arguments.of(WORDS.stream().collect(Multimaps.toSetMultimap(word -> word.charAt(0), word -> word))),
                Arguments.of(
                        WORDS.parallelStream().collect(Multimaps.toSetMultimap(word -> word.charAt(0), word -> word))),
                Arguments.of(Multimaps.setMultimapCopy(
                        WORDS.stream().collect(Multimaps.toListMultimap(word -> word.charAt(0), word -> word)))));
    }

    /** One multimap of each class: how they were built only matters for their order. */
    static Stream<Arguments> eachKind() {
        return Stream.of(
                Arguments.of(WORDS.stream().collect(Multimaps.toListMultimap(word -> word.charAt(0), word -> word))),
                Arguments.of(WORDS.stream().collect(Multimaps.toSetMultimap(word -> word.charAt(0), word -> word))));
    }

    @ParameterizedTest
    @MethodSource("everyListFactory")
    void listMultimapsIterateInEncounterOrder(ListMultimap<Character, String> multimap) {
        assertThat(multimap.keySet()).containsExactly('b', 'a', 'c');
        assertThat(multimap.get('b')).containsExactly("bob", "bill", "bob");
        assertThat(multimap.values()).containsExactly("bob", "bill", "bob", "alice", "amy", "carol");
        assertThat(multimap.entries()).first().isEqualTo(Map.entry('b', "bob"));
        assertThat(multimap).hasToString("{b=[bob, bill, bob], a=[alice, amy], c=[carol]}");
        assertThat(multimap.size()).isEqualTo(6);
        assertThat(multimap).isEqualTo(byInitial()).hasSameHashCodeAs(byInitial());
        assertConsistent(multimap);
    }

    @ParameterizedTest
    @MethodSource("everySetFactory")
    void setMultimapsIterateInEncounterOrderWithoutDuplicates(SetMultimap<Character, String> multimap) {
        var expected = HashSetMultimap.create(byInitial());

        assertThat(multimap.keySet()).containsExactly('b', 'a', 'c');
        assertThat(multimap.get('b')).containsExactly("bob", "bill");
        assertThat(multimap.values()).containsExactly("bob", "bill", "alice", "amy", "carol");
        assertThat(multimap).hasToString("{b=[bob, bill], a=[alice, amy], c=[carol]}");
        assertThat(multimap.size()).isEqualTo(5);
        assertThat(multimap).isEqualTo(expected).hasSameHashCodeAs(expected);
        assertConsistent(multimap);
    }

    @ParameterizedTest
    @MethodSource("eachKind")
    void answersQueries(Multimap<Character, String> multimap) {
        assertThat(multimap.containsKey('a')).isTrue();
        assertThat(multimap.containsKey('z')).isFalse();
        assertThat(multimap.containsValue("amy")).isTrue();
        assertThat(multimap.containsValue("zoe")).isFalse();
        assertThat(multimap.containsEntry('a', "amy")).isTrue();
        assertThat(multimap.containsEntry('b', "amy")).isFalse();
        assertThat(multimap.get('z')).isEmpty();
        assertThat(multimap.asMap().get('z')).isNull();
        assertThat(multimap.entries().contains(Map.entry('c', "carol"))).isTrue();
        var visited = new ArrayList<String>();
        multimap.forEach((key, value) -> visited.add(value));
        assertThat(visited).containsExactlyElementsOf(multimap.values());
    }

    @ParameterizedTest
    @MethodSource("eachKind")
    void survivesSerializationWithItsOrder(Multimap<Character, String> multimap)
            throws IOException, ClassNotFoundException {
        Object copy = deserialize(serialize(multimap));

        assertThat(copy).isEqualTo(multimap).hasToString(multimap.toString());
        assertThat(copy.getClass()).isEqualTo(multimap.getClass());
    }

    // Passing null is the point.
    @ParameterizedTest
    @MethodSource("eachKind")
    @SuppressWarnings("NullAway")
    void throwWhenQueriedWithNull(Multimap<Character, String> multimap) {
        assertThatNullPointerException().isThrownBy(() -> multimap.containsKey(null));
        assertThatNullPointerException().isThrownBy(() -> multimap.containsValue(null));
        assertThatNullPointerException().isThrownBy(() -> multimap.containsEntry('a', null));
        assertThatNullPointerException().isThrownBy(() -> multimap.containsEntry(null, "amy"));
        assertThatNullPointerException().isThrownBy(() -> multimap.get(null));
        assertThatNullPointerException().isThrownBy(() -> multimap.get('a').contains(null));
        assertThatNullPointerException().isThrownBy(() -> multimap.get('z').contains(null));
        assertThatNullPointerException().isThrownBy(() -> multimap.keySet().contains(null));
        assertThatNullPointerException().isThrownBy(() -> multimap.values().contains(null));
        assertThatNullPointerException().isThrownBy(() -> multimap.entries().contains(null));
        assertThatNullPointerException().isThrownBy(() -> multimap.asMap().get(null));
        assertThatNullPointerException().isThrownBy(() -> multimap.asMap().containsKey(null));
        Map<?, ?> asMap = multimap.asMap();
        assertThatNullPointerException().isThrownBy(() -> asMap.containsValue(null));
    }

    @Test
    void listMultimapsHaveListValuesAndSetMultimapsSetValues() {
        ListMultimap<Character, String> list = Multimaps.listMultimapCopy(byInitial());
        SetMultimap<Character, String> set = Multimaps.setMultimapCopy(byInitial());

        assertThat(list.get('b')).isInstanceOf(List.class);
        assertThat(list.asMap().get('b')).containsExactly("bob", "bill", "bob");
        assertThat(set.get('b')).isInstanceOf(Set.class);
        assertThat(set.entries()).isInstanceOf(Set.class).hasSize(5);
        assertThat(set.asMap().get('b')).containsExactly("bob", "bill");
        assertThat(list).isNotEqualTo(set);
    }

    @Test
    void doNotFollowLaterChangesToTheSource() {
        var source = byInitial();
        ListMultimap<Character, String> copy = Multimaps.listMultimapCopy(source);

        source.put('a', "anna");
        source.removeAll('b');

        assertThat(copy).isEqualTo(byInitial());
    }

    @Test
    void keepTheEncounterOrderOfALargeParallelStream() {
        List<Integer> numbers =
                IntStream.rangeClosed(1, 10_000).map(i -> 10_001 - i).boxed().toList();

        ListMultimap<Integer, Integer> list =
                numbers.parallelStream().collect(Multimaps.toListMultimap(i -> i % 7, i -> i));
        SetMultimap<Integer, Integer> set =
                numbers.parallelStream().collect(Multimaps.toSetMultimap(i -> i % 7, i -> i / 7));

        assertThat(list.keySet())
                .containsExactlyElementsOf(
                        numbers.subList(0, 7).stream().map(i -> i % 7).toList());
        assertThat(list.get(0))
                .containsExactlyElementsOf(
                        numbers.stream().filter(i -> i % 7 == 0).toList());
        assertThat(set.get(0))
                .containsExactlyElementsOf(
                        numbers.stream().filter(i -> i % 7 == 0).map(i -> i / 7).toList());
        assertConsistent(list);
        assertConsistent(set);
    }

    @Test
    void collectEmptyStreams() {
        assertThat(Stream.<String>empty()
                        .collect(Multimaps.toListMultimap(s -> s, s -> s))
                        .isEmpty())
                .isTrue();
        assertThat(Stream.<String>empty()
                        .collect(Multimaps.toSetMultimap(s -> s, s -> s))
                        .isEmpty())
                .isTrue();
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectNullKeysAndValues() {
        assertThatNullPointerException()
                .isThrownBy(() -> Stream.of("a").collect(Multimaps.toListMultimap(s -> null, s -> s)))
                .withMessage("key");
        assertThatNullPointerException()
                .isThrownBy(() -> Stream.of("a").collect(Multimaps.toSetMultimap(s -> s, s -> null)))
                .withMessage("value");
        assertThatNullPointerException()
                .isThrownBy(() -> Multimaps.listMultimapCopy(null))
                .withMessage("multimap");
        assertThatNullPointerException()
                .isThrownBy(() -> Multimaps.setMultimapCopy(null))
                .withMessage("multimap");
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectNullFunctions() {
        assertThatNullPointerException()
                .isThrownBy(() -> Multimaps.toListMultimap(null, Function.identity()))
                .withMessage("keyMapper");
        assertThatNullPointerException()
                .isThrownBy(() -> Multimaps.toListMultimap(Function.identity(), null))
                .withMessage("valueMapper");
        assertThatNullPointerException()
                .isThrownBy(() -> Multimaps.toSetMultimap(null, Function.identity()))
                .withMessage("keyMapper");
        assertThatNullPointerException()
                .isThrownBy(() -> Multimaps.toSetMultimap(Function.identity(), null))
                .withMessage("valueMapper");
    }

    @Nested
    class Unmodifiable {

        static Stream<Arguments> mutators() {
            return Stream.<Consumer<Multimap<Character, String>>>of(
                            multimap -> multimap.put('a', "anna"),
                            multimap -> multimap.put('a', "amy"),
                            multimap -> multimap.putAll('a', List.of()),
                            multimap -> multimap.putAll(HashListMultimap.create()),
                            multimap -> multimap.remove('a', "amy"),
                            multimap -> multimap.remove('z', "zoe"),
                            multimap -> multimap.removeAll('z'),
                            multimap -> multimap.replaceValues('a', List.of("alice", "amy")),
                            Multimap::clear,
                            multimap -> multimap.get('a').add("anna"),
                            multimap -> multimap.get('a').remove("zoe"),
                            multimap -> multimap.get('z').clear(),
                            multimap -> multimap.get('a').removeIf(value -> false),
                            multimap -> multimap.keySet().remove('z'),
                            multimap -> multimap.keySet().clear(),
                            multimap -> multimap.values().remove("zoe"),
                            multimap -> multimap.values().removeIf(value -> false),
                            multimap -> multimap.entries().remove(Map.entry('z', "zoe")),
                            multimap -> multimap.entries().clear(),
                            multimap -> multimap.asMap().remove('z'),
                            multimap -> multimap.asMap().clear(),
                            multimap ->
                                    requireNonNull(multimap.asMap().get('a')).clear(),
                            multimap ->
                                    multimap.asMap().values().iterator().next().clear(),
                            multimap -> multimap.asMap().entrySet().removeIf(entry -> false),
                            multimap -> {
                                var iterator = multimap.values().iterator();
                                iterator.next();
                                iterator.remove();
                            },
                            multimap -> {
                                var iterator = multimap.get('a').iterator();
                                iterator.next();
                                iterator.remove();
                            },
                            multimap -> {
                                var iterator = multimap.keySet().iterator();
                                iterator.next();
                                iterator.remove();
                            })
                    .map(Arguments::of);
        }

        static Stream<Arguments> everyMultimapAndMutator() {
            return eachKind()
                    .flatMap(multimap -> mutators().map(mutator -> Arguments.of(multimap.get()[0], mutator.get()[0])));
        }

        @ParameterizedTest
        @MethodSource("everyMultimapAndMutator")
        void throwsOnEveryMutationEvenANoOp(
                Multimap<Character, String> multimap, Consumer<Multimap<Character, String>> mutation) {
            var before = multimap.toString();

            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> mutation.accept(multimap));
            assertThat(multimap).hasToString(before);
        }

        @Test
        void throwsOnListMutations() {
            ListMultimap<Character, String> multimap = Multimaps.listMultimapCopy(byInitial());

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> multimap.get('a').set(0, "anna"));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> multimap.get('a').sort(Comparator.naturalOrder()));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> requireNonNull(multimap.asMap().get('a')).set(0, "anna"));
        }
    }
}
