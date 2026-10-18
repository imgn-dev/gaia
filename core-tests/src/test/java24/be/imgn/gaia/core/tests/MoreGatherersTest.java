package be.imgn.gaia.core.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import be.imgn.gaia.core.collect.MoreGatherers;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MoreGatherersTest {

    @Test
    void keepsTheInstancesCastToTheType() {
        Stream<Object> things = Stream.of("a", 1, "b", 2.0, 'c');

        List<String> strings =
                things.gather(MoreGatherers.instanceOf(String.class)).toList();

        assertThat(strings).containsExactly("a", "b");
    }

    @Test
    void keepsTheInstancesOfSubtypes() {
        Stream<Object> things = Stream.of(1, 2.5, "three", 4L);

        List<Number> numbers =
                things.gather(MoreGatherers.instanceOf(Number.class)).toList();

        assertThat(numbers).containsExactly(1, 2.5, 4L);
    }

    @Test
    void worksOnAStreamOfASupertype() {
        Stream<CharSequence> sequences = Stream.of("a", CharBuffer.wrap("b"), "c");

        List<String> strings =
                sequences.gather(MoreGatherers.instanceOf(String.class)).toList();

        assertThat(strings).containsExactly("a", "c");
    }

    @Test
    void dropsNullElements() {
        assertThat(Arrays.asList("a", null, "b").stream()
                        .gather(MoreGatherers.instanceOf(String.class))
                        .toList())
                .containsExactly("a", "b");
    }

    @Test
    void keepsTheEncounterOrderOfAParallelStream() {
        List<Object> things = Stream.iterate(0, i -> i + 1)
                .limit(10_000)
                .<Object>map(i -> i % 2 == 0 ? i : "odd " + i)
                .toList();

        assertThat(things.parallelStream()
                        .gather(MoreGatherers.instanceOf(Integer.class))
                        .toList())
                .containsExactlyElementsOf(things.stream()
                        .filter(Integer.class::isInstance)
                        .map(Integer.class::cast)
                        .toList());
    }

    @Test
    void keepsTheFirstElementOfEachKey() {
        assertThat(Stream.of("apple", "avocado", "banana", "blueberry", "cherry", "apricot")
                        .gather(MoreGatherers.distinctBy(s -> s.charAt(0)))
                        .toList())
                .containsExactly("apple", "banana", "cherry");
    }

    @Test
    void keepsNullElementsAndNullKeys() {
        assertThat(Arrays.asList("a", null, "b", null).stream()
                        .gather(MoreGatherers.distinctBy(s -> s))
                        .toList())
                .containsExactly("a", null, "b");
    }

    @Test
    void takesWhileThePredicateHoldsAndTheFirstElementThatFailsIt() {
        assertThat(Stream.of(1, 2, 3, 4, 1)
                        .gather(MoreGatherers.takeWhileInclusive(i -> i < 3))
                        .toList())
                .containsExactly(1, 2, 3);
        assertThat(Stream.of(1, 2)
                        .gather(MoreGatherers.takeWhileInclusive(i -> i < 3))
                        .toList())
                .containsExactly(1, 2);
    }

    @Test
    void stopsPullingAfterTheFirstElementThatFailsThePredicate() {
        var pulled = new ArrayList<Integer>();

        var taken = Stream.iterate(0, i -> i + 1)
                .peek(pulled::add)
                .gather(MoreGatherers.takeWhileInclusive(i -> i < 2))
                .toList();

        assertThat(taken).containsExactly(0, 1, 2);
        assertThat(pulled).containsExactly(0, 1, 2);
    }

    @Test
    void groupsRunsOfEqualKeys() {
        var runs = Stream.of("a1", "a2", "b1", "a3")
                .gather(MoreGatherers.groupRuns(s -> s.charAt(0)))
                .toList();

        assertThat(runs).containsExactly(List.of("a1", "a2"), List.of("b1"), List.of("a3"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> runs.getFirst().add("a4"));
    }

    @Test
    void groupsRunsOfNullKeys() {
        assertThat(Arrays.asList("a", null, null, "b").stream()
                        .gather(MoreGatherers.groupRuns(s -> s))
                        .toList())
                .containsExactly(List.of("a"), Arrays.asList(null, null), List.of("b"));
    }

    @Test
    void takesTheLastElements() {
        assertThat(Stream.of(1, 2, 3, 4, 5).gather(MoreGatherers.takeLast(2)).toList())
                .containsExactly(4, 5);
        assertThat(Stream.of(1, 2).gather(MoreGatherers.takeLast(5)).toList()).containsExactly(1, 2);
        assertThat(Stream.of(1, 2).gather(MoreGatherers.takeLast(0)).toList()).isEmpty();
        assertThat(Arrays.asList(1, null, 3).stream()
                        .gather(MoreGatherers.takeLast(2))
                        .toList())
                .containsExactly(null, 3);
    }

    @Test
    void dropsTheLastElements() {
        assertThat(Stream.of(1, 2, 3, 4, 5).gather(MoreGatherers.dropLast(2)).toList())
                .containsExactly(1, 2, 3);
        assertThat(Stream.of(1, 2).gather(MoreGatherers.dropLast(5)).toList()).isEmpty();
        assertThat(Stream.of(1, 2).gather(MoreGatherers.dropLast(0)).toList()).containsExactly(1, 2);
        assertThat(Arrays.asList(null, 2, 3).stream()
                        .gather(MoreGatherers.dropLast(1))
                        .toList())
                .containsExactly(null, 2);
    }

    @Test
    void dropsElementsEqualToTheOneBefore() {
        assertThat(Stream.of(1, 1, 2, 2, 2, 1)
                        .gather(MoreGatherers.distinctAdjacent())
                        .toList())
                .containsExactly(1, 2, 1);
        assertThat(Arrays.asList(null, null, 1, null).stream()
                        .gather(MoreGatherers.distinctAdjacent())
                        .toList())
                .containsExactly(null, 1, null);
    }

    @Test
    void dropsElementsWhoseKeyEqualsTheOneBefore() {
        assertThat(Stream.of("apple", "avocado", "banana", "apricot")
                        .gather(MoreGatherers.distinctAdjacentBy(s -> s.charAt(0)))
                        .toList())
                .containsExactly("apple", "banana", "apricot");
    }

    @Test
    void mapsWithTheIndex() {
        assertThat(Stream.of("a", "b", "c")
                        .gather(MoreGatherers.mapWithIndex((i, s) -> i + ":" + s))
                        .toList())
                .containsExactly("0:a", "1:b", "2:c");
    }

    @Test
    void filtersWithTheIndex() {
        assertThat(Stream.of("a", "b", "c", "d")
                        .gather(MoreGatherers.filterWithIndex((i, s) -> i % 2 == 1 || s.equals("c")))
                        .toList())
                .containsExactly("b", "c", "d");
    }

    @Test
    void keepsEveryNthElementStartingWithTheFirst() {
        assertThat(Stream.of(1, 2, 3, 4, 5).gather(MoreGatherers.everyNth(2)).toList())
                .containsExactly(1, 3, 5);
        assertThat(Stream.of(1, 2, 3).gather(MoreGatherers.everyNth(1)).toList())
                .containsExactly(1, 2, 3);
    }

    @Test
    void keepsTheGreatestElementsGreatestFirst() {
        assertThat(Stream.of(3, 1, 4, 1, 5, 9, 2)
                        .gather(MoreGatherers.max(3, Comparator.naturalOrder()))
                        .toList())
                .containsExactly(9, 5, 4);
        assertThat(Stream.of(2, 1)
                        .gather(MoreGatherers.max(5, Comparator.naturalOrder()))
                        .toList())
                .containsExactly(2, 1);
        assertThat(Stream.of(2, 1)
                        .gather(MoreGatherers.max(0, Comparator.naturalOrder()))
                        .toList())
                .isEmpty();
    }

    @Test
    void keepsTheLeastElementsLeastFirst() {
        assertThat(Stream.of(3, 1, 4, 1, 5, 9, 2)
                        .gather(MoreGatherers.min(3, Comparator.naturalOrder()))
                        .toList())
                .containsExactly(1, 1, 2);
    }

    @Test
    void keepsTheEarliestOfEqualElements() {
        Comparator<String> byLength = Comparator.comparingInt(String::length);

        assertThat(Stream.of("bb", "a", "cc", "dd", "e")
                        .gather(MoreGatherers.max(2, byLength))
                        .toList())
                .containsExactly("bb", "cc");
        assertThat(Stream.of("bb", "a", "cc", "e", "f")
                        .gather(MoreGatherers.min(2, byLength))
                        .toList())
                .containsExactly("a", "e");
    }

    @Test
    void ordersNullsWithTheComparator() {
        assertThat(Arrays.asList(2, null, 1).stream()
                        .gather(MoreGatherers.max(2, Comparator.nullsLast(Comparator.<Integer>naturalOrder())))
                        .toList())
                .containsExactly(null, 2);
    }

    @Test
    void producesNothingFromAnEmptyStream() {
        assertThat(Stream.<String>empty()
                        .gather(MoreGatherers.groupRuns(s -> s))
                        .toList())
                .isEmpty();
        assertThat(Stream.empty().gather(MoreGatherers.takeLast(2)).toList()).isEmpty();
        assertThat(Stream.<Integer>empty()
                        .gather(MoreGatherers.max(2, Comparator.naturalOrder()))
                        .toList())
                .isEmpty();
    }

    @Test
    void givesTheSameResultsInParallel() {
        var numbers = Stream.iterate(0, i -> i + 1).limit(100_000).toList();
        Comparator<Integer> byTens = Comparator.comparingInt(i -> i / 10);

        assertThat(numbers.parallelStream()
                        .gather(MoreGatherers.takeWhileInclusive(i -> i < 70_000))
                        .toList())
                .isEqualTo(numbers.subList(0, 70_001));
        assertThat(numbers.parallelStream().gather(MoreGatherers.takeLast(3)).toList())
                .containsExactly(99_997, 99_998, 99_999);
        assertThat(numbers.parallelStream().gather(MoreGatherers.max(3, byTens)).toList())
                .containsExactly(99_990, 99_991, 99_992);
        assertThat(numbers.parallelStream().gather(MoreGatherers.min(3, byTens)).toList())
                .containsExactly(0, 1, 2);
        assertThat(numbers.parallelStream()
                        .gather(MoreGatherers.everyNth(10_000))
                        .toList())
                .containsExactly(0, 10_000, 20_000, 30_000, 40_000, 50_000, 60_000, 70_000, 80_000, 90_000);
        assertThat(numbers.parallelStream()
                        .gather(MoreGatherers.dropLast(99_998))
                        .toList())
                .containsExactly(0, 1);
    }

    @Test
    void endsAnInfiniteStreamWhenItWantsNoElement() {
        assertThat(Stream.iterate(0, i -> i + 1)
                        .gather(MoreGatherers.takeLast(0))
                        .toList())
                .isEmpty();
        assertThat(Stream.iterate(0, i -> i + 1)
                        .gather(MoreGatherers.max(0, Comparator.naturalOrder()))
                        .toList())
                .isEmpty();
    }

    @Test
    void stopsPushingWhenTheDownstreamWantsNoMore() {
        assertThat(Stream.of("a1", "a2", "b1", "c1")
                        .gather(MoreGatherers.groupRuns(s -> s.charAt(0)))
                        .limit(1)
                        .toList())
                .containsExactly(List.of("a1", "a2"));
        assertThat(Stream.of(1, 2, 3, 4)
                        .gather(MoreGatherers.takeLast(3))
                        .limit(1)
                        .toList())
                .containsExactly(2);
        assertThat(Stream.of(1, 2, 3, 4)
                        .gather(MoreGatherers.max(3, Comparator.naturalOrder()))
                        .limit(1)
                        .toList())
                .containsExactly(4);
    }

    @Test
    void rejectsInvalidCounts() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreGatherers.takeLast(-1))
                .withMessage("n must not be negative: -1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreGatherers.dropLast(-1))
                .withMessage("n must not be negative: -1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreGatherers.max(-1, Comparator.<Integer>naturalOrder()))
                .withMessage("n must not be negative: -1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreGatherers.min(-1, Comparator.<Integer>naturalOrder()))
                .withMessage("n must not be negative: -1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MoreGatherers.everyNth(0))
                .withMessage("n must be positive: 0");
    }
}
