package be.imgn.gaia.core.random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.security.SecureRandom;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RandomStringsTest {

    @Test
    void stringTakesTheCharactersAtTheRandomIndexes() {
        var random = new ScriptedRandom(0, 9, 4, 4);

        assertThat(RandomStrings.string(4, RandomStrings.DIGITS, random)).isEqualTo("0944");
        assertThat(random.isExhausted()).isTrue();
    }

    @Test
    void stringUsesOnlyTheAlphabet() {
        var string = RandomStrings.string(1_000, RandomStrings.UNAMBIGUOUS, new SplittableRandom(1));

        assertThat(string).hasSize(1_000).matches("[2-9A-HJ-NP-Z]+");
        assertThat(string.chars().distinct().count()).isEqualTo(32);
    }

    @Test
    void anEmptyStringUsesNoRandomness() {
        assertThat(RandomStrings.string(0, "ab", new ScriptedRandom())).isEmpty();
    }

    @Test
    void alphabetsHaveNoCharacterTwice() {
        assertThat(RandomStrings.DIGITS).hasSize(10);
        assertThat(RandomStrings.HEXADECIMAL).hasSize(16);
        assertThat(RandomStrings.ALPHANUMERIC).hasSize(62);
        assertThat(RandomStrings.URL_SAFE).hasSize(64);
        assertThat(RandomStrings.UNAMBIGUOUS).hasSize(32).doesNotContain("0", "O", "1", "I");
        for (var alphabet : new String[] {
            RandomStrings.DIGITS,
            RandomStrings.HEXADECIMAL,
            RandomStrings.ALPHANUMERIC,
            RandomStrings.URL_SAFE,
            RandomStrings.UNAMBIGUOUS
        }) {
            assertThat(alphabet.chars().distinct().count()).isEqualTo(alphabet.length());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0123456789abcdef, 0",
        "1, 0123456789abcdef, 1",
        "4, 0123456789abcdef, 1",
        "5, 0123456789abcdef, 2",
        "128, 0123456789abcdef, 32",
        "129, 0123456789abcdef, 33",
        "128, 0123456789, 39",
        "10, 0123456789, 4",
        "9, 0123456789, 3",
        "1, ab, 1",
        "64, ab, 64",
        "4000, ab, 4000",
    })
    void lengthIsTheLeastOneThatHasEnoughRandomness(int bits, String alphabet, int length) {
        assertThat(RandomStrings.length(bits, alphabet)).isEqualTo(length);
    }

    @Test
    void tokenHasEnoughRandomness() {
        var token = RandomStrings.token(128, new SecureRandom());

        assertThat(token).hasSize(22).matches("[0-9A-Za-z_-]+");
        assertThat(RandomStrings.token(128, new SecureRandom())).isNotEqualTo(token);
        assertThat(RandomStrings.token(0, new ScriptedRandom())).isEmpty();
    }

    @Test
    void invalidArgumentsAreRejected() {
        var random = new SplittableRandom(1);

        assertThatIllegalArgumentException().isThrownBy(() -> RandomStrings.string(-1, "ab", random));
        assertThatIllegalArgumentException().isThrownBy(() -> RandomStrings.string(1, "", random));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RandomStrings.string(1, "aba", random))
                .withMessageContaining("'a' twice");
        assertThatIllegalArgumentException().isThrownBy(() -> RandomStrings.string(1, "a😀", random));
        assertThatIllegalArgumentException().isThrownBy(() -> RandomStrings.length(1, "a"));
        assertThatIllegalArgumentException().isThrownBy(() -> RandomStrings.length(-1, "ab"));
        assertThatIllegalArgumentException().isThrownBy(() -> RandomStrings.token(-1, random));
    }

    @Test
    void oneCharacterMakesAStringWithNoRandomness() {
        assertThat(RandomStrings.string(3, "a", new ScriptedRandom(0, 0, 0))).isEqualTo("aaa");
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullsAreRejected() {
        assertThatNullPointerException().isThrownBy(() -> RandomStrings.string(1, "ab", null));
        assertThatNullPointerException().isThrownBy(() -> RandomStrings.string(1, null, new SplittableRandom(1)));
        assertThatNullPointerException().isThrownBy(() -> RandomStrings.token(1, null));
    }
}
