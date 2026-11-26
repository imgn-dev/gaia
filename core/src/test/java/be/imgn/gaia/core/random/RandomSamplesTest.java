package be.imgn.gaia.core.random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.util.HashSet;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RandomSamplesTest {

    private static final List<Integer> TEN = IntStream.range(0, 10).boxed().toList();
    private static final int RUNS = 50_000;

    @Test
    void sampleTakesDifferentElements() {
        var sample = RandomSamples.sample(TEN, 4, new SplittableRandom(1));

        assertThat(sample).hasSize(4).doesNotHaveDuplicates().isSubsetOf(TEN);
    }

    @Test
    void sampleTakesEverythingWhenThereAreTooFew() {
        assertThat(RandomSamples.sample(TEN, 20, new SplittableRandom(1))).containsExactlyInAnyOrderElementsOf(TEN);
        assertThat(RandomSamples.sample(TEN, 0, new ScriptedRandom())).isEmpty();
        assertThat(RandomSamples.sample(List.of(), 3, new ScriptedRandom())).isEmpty();
    }

    @Test
    void sampleTakesEachElementAsOften() {
        var random = new SplittableRandom(2);
        int[] taken = new int[10];

        for (int i = 0; i < RUNS; i++) {
            RandomSamples.sample(TEN, 3, random).forEach(element -> taken[element]++);
        }

        for (int count : taken) {
            assertThat((double) count / RUNS).isCloseTo(0.3, within(0.01));
        }
    }

    @Test
    void weightedSampleTakesDifferentElements() {
        var sample = RandomSamples.weightedSample(TEN, element -> element + 1, 4, new SplittableRandom(3));

        assertThat(sample).hasSize(4).doesNotHaveDuplicates().isSubsetOf(TEN);
    }

    @Test
    void weightedSampleNeverTakesAWeightOfZero() {
        var sample = RandomSamples.weightedSample(TEN, element -> element % 2, 10, new SplittableRandom(4));

        assertThat(sample).containsExactlyInAnyOrder(1, 3, 5, 7, 9);
    }

    @Test
    void weightedSampleTakesTheFirstElementInProportionToTheWeights() {
        var elements = List.of("a", "b", "c");
        var random = new SplittableRandom(5);
        int a = 0;
        int ab = 0;

        for (int i = 0; i < RUNS; i++) {
            var sample = RandomSamples.<String>weightedSample(
                    elements, element -> element.equals("a") ? 6 : element.equals("b") ? 3 : 1, 2, random);
            a += sample.getFirst().equals("a") ? 1 : 0;
            ab += sample.equals(List.of("a", "b")) ? 1 : 0;
        }

        // "a" first is 6 in 10; then "b" is 3 in the 4 that are left.
        assertThat((double) a / RUNS).isCloseTo(0.6, within(0.01));
        assertThat((double) ab / RUNS).isCloseTo(0.45, within(0.01));
    }

    @Test
    void weightedSampleUsesOneNumberPerPositiveWeight() {
        var random = new ScriptedRandom(0.5, 0.1, 0.9);

        // Equal weights: the smallest numbers are taken first.
        var sample = RandomSamples.weightedSample(
                List.of("a", "never", "b", "c"), element -> element.equals("never") ? 0 : 1, 2, random);

        assertThat(sample).containsExactly("b", "a");
        assertThat(random.isExhausted()).isTrue();
    }

    @Test
    void toSampleTakesDifferentElementsOfAStream() {
        var sample = IntStream.range(0, 10_000).boxed().collect(RandomSamples.toSample(100, new SplittableRandom(6)));

        assertThat(sample).hasSize(100).doesNotHaveDuplicates().allMatch(element -> element < 10_000);
        assertThat(new HashSet<>(sample)).anyMatch(element -> element >= 5_000);
    }

    @Test
    void toSampleTakesEverythingWhenThereAreTooFew() {
        assertThat(TEN.stream().collect(RandomSamples.toSample(20, new ScriptedRandom())))
                .containsExactlyElementsOf(TEN);
        assertThat(TEN.stream().collect(RandomSamples.toSample(0, new ScriptedRandom())))
                .isEmpty();
    }

    @Test
    void toSampleTakesEachElementAsOften() {
        var random = new SplittableRandom(7);
        int[] taken = new int[10];

        for (int i = 0; i < RUNS; i++) {
            TEN.stream().collect(RandomSamples.toSample(3, random)).forEach(element -> taken[element]++);
        }

        for (int count : taken) {
            assertThat((double) count / RUNS).isCloseTo(0.3, within(0.01));
        }
    }

    @Test
    void toSampleDoesNotSupportParallelStreams() {
        var collector = RandomSamples.<Integer>toSample(3, new SplittableRandom(8));

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> IntStream.range(0, 100_000).boxed().parallel().collect(collector));
    }

    @Test
    void invalidArgumentsAreRejected() {
        var random = new SplittableRandom(9);

        assertThatIllegalArgumentException().isThrownBy(() -> RandomSamples.sample(TEN, -1, random));
        assertThatIllegalArgumentException().isThrownBy(() -> RandomSamples.toSample(-1, random));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomSamples.weightedSample(TEN, element -> 1, -1, random));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomSamples.weightedSample(TEN, element -> -1, 1, random));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomSamples.weightedSample(TEN, element -> Double.NaN, 1, random));
    }
}
