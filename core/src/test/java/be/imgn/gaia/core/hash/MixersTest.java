package be.imgn.gaia.core.hash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class MixersTest {

    @Test
    void mix64IsStable() {
        // These values are part of the contract: they must never change.
        assertThat(Mixers.mix64(0)).isZero();
        assertThat(Mixers.mix64(1)).isEqualTo(6238072747940578789L);
        assertThat(Mixers.mix64(2)).isEqualTo(-2606959012126976886L);
        assertThat(Mixers.mix64(-1)).isEqualTo(-5417735806833148549L);
    }

    @Test
    void mix64IsTheMixingOfSplittableRandom() {
        for (long seed : new long[] {0, 1, 42, -7, Long.MAX_VALUE}) {
            assertThat(Mixers.mix64(seed + Mixers.GOLDEN_GAMMA)).isEqualTo(new SplittableRandom(seed).nextLong());
        }
    }

    @Test
    void mix64GivesDifferentResultsForDifferentNumbers() {
        var results = new HashSet<Long>();

        for (long value = -50_000; value < 50_000; value++) {
            results.add(Mixers.mix64(value));
        }

        assertThat(results).hasSize(100_000);
    }

    @Test
    void mix64ChangesAboutHalfOfTheBitsWhenOneBitChanges() {
        var random = new SplittableRandom(1);
        long changed = 0;
        int runs = 10_000;

        for (int run = 0; run < runs; run++) {
            long value = random.nextLong();
            long flipped = value ^ (1L << random.nextInt(Long.SIZE));
            changed += Long.bitCount(Mixers.mix64(value) ^ Mixers.mix64(flipped));
        }

        assertThat((double) changed / runs).isBetween(31.0, 33.0);
    }
}
