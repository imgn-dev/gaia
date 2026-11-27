package be.imgn.gaia.core.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.SplittableRandom;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FeistelTest {

    @Test
    void scramblingIsStable() {
        // These values are part of the contract: they must never change.
        var network = Feistel.of(32, 1, 2, 3, 4, 5, 6);
        var odd = Feistel.of(7, 9, 8, 7);

        assertThat(network.apply(0)).isEqualTo(1378769405L);
        assertThat(network.apply(1)).isEqualTo(1863283837L);
        assertThat(network.apply(42)).isEqualTo(3438716234L);
        assertThat(odd.apply(0)).isEqualTo(74);
        assertThat(odd.apply(127)).isEqualTo(54);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 7, 8, 11, 12, 13})
    void everyNumberComesOutOnce(int bits) {
        for (int rounds = 1; rounds <= 7; rounds++) {
            var network = Feistel.of(
                    bits,
                    new SplittableRandom(bits * 31L + rounds).longs(rounds).toArray());

            assertThat(LongStream.range(0, 1L << bits)
                            .map(network::apply)
                            .sorted()
                            .toArray())
                    .as("%d bits, %d rounds", bits, rounds)
                    .isEqualTo(LongStream.range(0, 1L << bits).toArray());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 16, 31, 32, 33, 62, 63})
    void invertUndoesApply(int bits) {
        var random = new SplittableRandom(bits);
        for (int rounds = 1; rounds <= 8; rounds++) {
            var network = Feistel.of(bits, random.longs(rounds).toArray());
            for (int i = 0; i < 200; i++) {
                long value = random.nextLong() >>> (Long.SIZE - bits);
                long scrambled = network.apply(value);

                assertThat(scrambled).isBetween(0L, (1L << bits) - 1);
                assertThat(network.invert(scrambled)).isEqualTo(value);
                assertThat(network.apply(network.invert(value))).isEqualTo(value);
            }
        }
    }

    @Test
    void theKeysDecideTheOrder() {
        var one = Feistel.of(16, 1, 2, 3, 4, 5, 6);
        var other = Feistel.of(16, 1, 2, 3, 4, 5, 7);

        assertThat(LongStream.range(0, 100)
                        .filter(value -> one.apply(value) == other.apply(value))
                        .count())
                .isLessThan(5);
        assertThat(LongStream.range(0, 1L << 16)
                        .filter(value -> one.apply(value) == value)
                        .count())
                .isLessThan(10);
    }

    @Test
    void consecutiveNumbersComeOutScattered() {
        var network = Feistel.of(32, new SplittableRandom(1).longs(6).toArray());
        long differing = 0;

        for (long value = 0; value < 1_000; value++) {
            differing += Long.bitCount(network.apply(value) ^ network.apply(value + 1));
        }

        // About half of the 32 bits change from a number to the next one.
        assertThat(differing / 1_000.0).isBetween(15.0, 17.0);
    }

    @Test
    void theNetworkKeepsACopyOfItsKeys() {
        long[] keys = {1, 2, 3, 4};
        var network = Feistel.of(16, keys);
        long scrambled = network.apply(7);

        keys[0] = 99;

        assertThat(network.apply(7)).isEqualTo(scrambled);
        assertThat(network.bits()).isEqualTo(16);
        assertThat(network.rounds()).isEqualTo(4);
    }

    @Test
    void invalidArgumentsAreRejected() {
        var network = Feistel.of(8, 1, 2, 3);

        assertThatIllegalArgumentException().isThrownBy(() -> Feistel.of(1, 1, 2, 3));
        assertThatIllegalArgumentException().isThrownBy(() -> Feistel.of(64, 1, 2, 3));
        assertThatIllegalArgumentException().isThrownBy(() -> Feistel.of(8));
        assertThatIllegalArgumentException().isThrownBy(() -> network.apply(256));
        assertThatIllegalArgumentException().isThrownBy(() -> network.apply(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> network.invert(256));
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullKeysAreRejected() {
        assertThatNullPointerException().isThrownBy(() -> Feistel.of(8, (long[]) null));
    }
}
