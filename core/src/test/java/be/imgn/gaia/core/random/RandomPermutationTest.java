package be.imgn.gaia.core.random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.util.SplittableRandom;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RandomPermutationTest {

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5, 16, 17, 100, 1_000, 4_096, 4_097, 10_007, 100_000})
    void everyNumberComesUpOnce(long size) {
        var permutation = RandomPermutation.of(size, new SplittableRandom(size));

        assertThat(permutation.stream().sorted().toArray())
                .isEqualTo(LongStream.range(0, size).toArray());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 7, 1_000, 4_097, 123_456_789_012L, 1L << 62})
    void indexOfUndoesGet(long size) {
        var permutation = RandomPermutation.of(size, new SplittableRandom(1));
        var random = new SplittableRandom(2);

        for (int i = 0; i < 200; i++) {
            long index = random.nextLong(size);
            long number = permutation.get(index);
            assertThat(number).isBetween(0L, size - 1);
            assertThat(permutation.indexOf(number)).isEqualTo(index);
        }
    }

    @Test
    void theOrderLooksRandom() {
        for (long size : new long[] {1_000, 100_000}) {
            var permutation = RandomPermutation.of(size, new SplittableRandom(3));
            long half = size / 2;

            assertThat(permutation.stream().limit(20).boxed())
                    .isNotEqualTo(LongStream.range(0, 20).boxed().toList());
            // Few numbers stay where they were, and the first half holds about half of the small numbers.
            assertThat(LongStream.range(0, size)
                            .filter(index -> permutation.get(index) == index)
                            .count())
                    .isLessThan(10);
            assertThat(permutation.stream()
                                    .limit(half)
                                    .filter(number -> number < half)
                                    .count()
                            / (double) half)
                    .isCloseTo(0.5, within(0.05));
        }
    }

    @Test
    void eachPositionGetsEveryNumberAsOften() {
        int size = 5;
        int runs = 50_000;
        var random = new SplittableRandom(4);
        var counts = new int[size][size];

        for (int run = 0; run < runs; run++) {
            var permutation = RandomPermutation.of(size, random);
            for (int index = 0; index < size; index++) {
                counts[index][(int) permutation.get(index)]++;
            }
        }

        for (var row : counts) {
            for (int count : row) {
                assertThat((double) count / runs).isCloseTo(0.2, within(0.01));
            }
        }
    }

    @Test
    void differentRandomnessGivesDifferentOrders() {
        var one = RandomPermutation.of(1_000, new SplittableRandom(5));
        var other = RandomPermutation.of(1_000, new SplittableRandom(6));
        var same = RandomPermutation.of(1_000, new SplittableRandom(5));

        assertThat(one.stream().boxed()).isNotEqualTo(other.stream().boxed().toList());
        assertThat(one.stream().boxed())
                .containsExactlyElementsOf(same.stream().boxed().toList());
    }

    @Test
    void aComputedPermutationSpreadsEveryNumberOverThePositions() {
        int size = 5_000;
        int runs = 400_000;
        var random = new SplittableRandom(9);
        var first = new int[size];
        int neighbours = 0;

        for (int run = 0; run < runs; run++) {
            var permutation = RandomPermutation.of(size, random);
            first[(int) permutation.get(0)]++;
            neighbours += Math.abs(permutation.get(1) - permutation.get(0)) == 1 ? 1 : 0;
        }

        // 80 expected per number, and two neighbours among the 4,999 other numbers.
        for (int count : first) {
            assertThat(count).isBetween(35, 140);
        }
        assertThat((double) neighbours / runs).isCloseTo(2.0 / 4_999, within(0.0002));
    }

    @Test
    void itTakesItsRandomnessWhenCreated() {
        var shuffled = new ScriptedRandom(2, 0, 0);
        var computed = new ScriptedRandom(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8);

        var small = RandomPermutation.of(4, shuffled);
        RandomPermutation.of(5_000, computed).get(3);

        assertThat(small.stream().boxed()).containsExactly(2L, 1L, 0L, 3L);
        assertThat(shuffled.isExhausted()).isTrue();
        assertThat(computed.isExhausted()).isTrue();
    }

    @Test
    void anEmptyPermutationHasNoNumber() {
        var permutation = RandomPermutation.of(0, new SplittableRandom(7));

        assertThat(permutation.size()).isZero();
        assertThat(permutation.stream()).isEmpty();
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> permutation.get(0));
    }

    @Test
    void invalidArgumentsAreRejected() {
        var permutation = RandomPermutation.of(10, new SplittableRandom(8));

        assertThatIllegalArgumentException().isThrownBy(() -> RandomPermutation.of(-1, new SplittableRandom(8)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomPermutation.of((1L << 62) + 1, new SplittableRandom(8)));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> permutation.get(10));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> permutation.get(-1));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> permutation.indexOf(10));
    }
}
