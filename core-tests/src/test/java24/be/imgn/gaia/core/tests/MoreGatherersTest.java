package be.imgn.gaia.core.tests;

import static org.assertj.core.api.Assertions.assertThat;

import be.imgn.gaia.core.collect.MoreGatherers;
import java.nio.CharBuffer;
import java.util.Arrays;
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
}
