package be.imgn.gaia.core.random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class SeedTest {

    private static final long DERIVED_0_0 = 5197578548964807871L;
    private static final long DERIVED_42_7 = 3603600226484403572L;
    private static final long MY_WORLD = 4275540920560346832L;
    private static final long DERIVED_42_TERRAIN = -1558953096145537250L;

    @Test
    void derivingIsStable() {
        // These values are part of the contract: they must never change.
        assertThat(Seed.of(0).derive(0).value()).isEqualTo(DERIVED_0_0);
        assertThat(Seed.of(42).derive(7).value()).isEqualTo(DERIVED_42_7);
        assertThat(Seed.of("my world").value()).isEqualTo(MY_WORLD);
        assertThat(Seed.of(42).derive("terrain").value()).isEqualTo(DERIVED_42_TERRAIN);
    }

    @Test
    void derivingDependsOnTheSeedAndOnTheKey() {
        var seed = Seed.of(42);

        assertThat(seed.derive(1)).isEqualTo(seed.derive(1)).isNotEqualTo(seed.derive(2));
        assertThat(seed.derive(1)).isNotEqualTo(Seed.of(43).derive(1));
        assertThat(seed.derive("a")).isNotEqualTo(seed.derive("b"));
    }

    @Test
    void derivingDependsOnTheOrderOfTheKeys() {
        var seed = Seed.of(42);

        assertThat(seed.derive(1).derive(2)).isNotEqualTo(seed.derive(2).derive(1));
        assertThat(seed.derive("ab")).isNotEqualTo(seed.derive("ba"));
        assertThat(seed.derive("ab")).isNotEqualTo(seed.derive("a").derive("b"));
        assertThat(seed.derive("")).isNotEqualTo(seed);
    }

    @Test
    void derivedSeedsDoNotCollideOnAGrid() {
        var seed = Seed.of("grid");
        var values = new HashSet<Long>();

        for (int x = -50; x < 50; x++) {
            for (int y = -50; y < 50; y++) {
                values.add(seed.derive(x).derive(y).value());
            }
        }

        assertThat(values).hasSize(10_000);
    }

    @Test
    void derivedSeedsUseAllTheirBits() {
        var seed = Seed.of(0);
        long ones = 0;

        for (int key = 0; key < 1_000; key++) {
            ones += Long.bitCount(seed.derive(key).value());
        }

        assertThat(ones / 1_000.0).isBetween(31.0, 33.0);
    }

    @Test
    void generatorsOfTheSameSeedGiveTheSameNumbers() {
        var seed = Seed.of("my world").derive("loot");

        assertThat(seed.generator().nextLong()).isEqualTo(seed.generator().nextLong());
        assertThat(seed.generator("L64X128MixRandom").nextLong())
                .isEqualTo(seed.generator("L64X128MixRandom").nextLong());
        assertThat(seed.generator().nextLong())
                .isNotEqualTo(seed.derive(1).generator().nextLong());
    }

    @Test
    void anUnknownAlgorithmIsRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> Seed.of(1).generator("NoSuchAlgorithm"));
    }

    @Test
    void randomSeedsDiffer() {
        assertThat(Seed.random()).isNotEqualTo(Seed.random());
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullsAreRejected() {
        assertThatNullPointerException().isThrownBy(() -> Seed.of(null));
        assertThatNullPointerException().isThrownBy(() -> Seed.of(1).derive(null));
    }
}
