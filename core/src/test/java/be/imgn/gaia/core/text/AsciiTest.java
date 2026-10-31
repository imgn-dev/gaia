package be.imgn.gaia.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.CharBuffer;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

class AsciiTest {

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";

    // Every code point, and a few ints that are none.
    private static void assertMatchesExactly(IntPredicate predicate, String members) {
        for (int c = -1; c <= Character.MAX_CODE_POINT + 1; c++) {
            assertThat(predicate.test(c)).as("U+%04X", c).isEqualTo(c >= 0 && members.indexOf(c) >= 0);
        }
        assertThat(predicate.test(Integer.MIN_VALUE)).isFalse();
        assertThat(predicate.test(Integer.MAX_VALUE)).isFalse();
    }

    @Test
    void classifiesLetters() {
        assertMatchesExactly(Ascii::isAlpha, UPPER + LOWER);
        assertMatchesExactly(Ascii::isUpperAlpha, UPPER);
        assertMatchesExactly(Ascii::isLowerAlpha, LOWER);
    }

    @Test
    void classifiesDigits() {
        assertMatchesExactly(Ascii::isDigit, DIGITS);
        assertMatchesExactly(Ascii::isAlphanumeric, UPPER + LOWER + DIGITS);
        assertMatchesExactly(Ascii::isHexDigit, DIGITS + "ABCDEFabcdef");
        assertMatchesExactly(Ascii::isUpperHexDigit, DIGITS + "ABCDEF");
        assertMatchesExactly(Ascii::isLowerHexDigit, DIGITS + "abcdef");
    }

    @Test
    void classifiesWhitespace() {
        assertMatchesExactly(Ascii::isWhitespace, "\t\n\f\r ");
    }

    @Test
    void changesTheCaseOfAsciiLettersOnly() {
        for (int c = -1; c <= Character.MAX_CODE_POINT + 1; c++) {
            int upper = UPPER.indexOf(c);
            int lower = LOWER.indexOf(c);
            assertThat(Ascii.toLowerCase(c)).as("U+%04X", c).isEqualTo(upper >= 0 ? LOWER.charAt(upper) : c);
            assertThat(Ascii.toUpperCase(c)).as("U+%04X", c).isEqualTo(lower >= 0 ? UPPER.charAt(lower) : c);
            if (c >= Character.MIN_VALUE && c <= Character.MAX_VALUE) {
                assertThat((int) Ascii.toLowerCase((char) c)).isEqualTo(Ascii.toLowerCase(c));
                assertThat((int) Ascii.toUpperCase((char) c)).isEqualTo(Ascii.toUpperCase(c));
            }
        }
    }

    @Test
    void leavesTheLettersThatOtherCaseMappingsFoldToAsciiAlone() {
        // Dotless i, long s and the Kelvin sign.
        assertThat(Ascii.toUpperCase("ıſ")).isEqualTo("ıſ");
        assertThat(Ascii.toLowerCase("K")).isEqualTo("K");
    }

    @Test
    void changesTheCaseOfText() {
        assertThat(Ascii.toLowerCase("PUBLıC Ünit")).isEqualTo("publıc Ünit");
        assertThat(Ascii.toUpperCase("publıc ünit")).isEqualTo("PUBLıC üNIT");
        assertThat(Ascii.toLowerCase(CharBuffer.wrap("Text/HTML"))).isEqualTo("text/html");
        assertThat(Ascii.toUpperCase(CharBuffer.wrap("utf-8"))).isEqualTo("UTF-8");
        assertThat(Ascii.toLowerCase("")).isEmpty();
    }

    @Test
    void returnsTheSameStringIfTheCaseDoesNotChange() {
        String lower = "text/html; charset=é";
        String upper = "DOCTYPE É";
        assertThat(Ascii.toLowerCase(lower)).isSameAs(lower);
        assertThat(Ascii.toUpperCase(upper)).isSameAs(upper);
    }

    @Test
    void comparesIgnoringAsciiCaseOnly() {
        assertThat(Ascii.equalsIgnoreCase("DocType", "DOCTYPE")).isTrue();
        assertThat(Ascii.equalsIgnoreCase(CharBuffer.wrap("a1-Z"), CharBuffer.wrap("A1-z")))
                .isTrue();
        assertThat(Ascii.equalsIgnoreCase("", "")).isTrue();
        assertThat(Ascii.equalsIgnoreCase("PUBLıC", "PUBLIC")).isFalse();
        assertThat(Ascii.equalsIgnoreCase("K", "k")).isFalse();
        assertThat(Ascii.equalsIgnoreCase("é", "É")).isFalse();
        assertThat(Ascii.equalsIgnoreCase("abc", "ab")).isFalse();
        // '@' and '`' are 32 apart, like 'A' and 'a'.
        assertThat(Ascii.equalsIgnoreCase("@", "`")).isFalse();
    }

    @Test
    void comparesPrefixesIgnoringAsciiCaseOnly() {
        assertThat(Ascii.startsWithIgnoreCase("Content-Type: text/html", "content-type"))
                .isTrue();
        assertThat(Ascii.startsWithIgnoreCase("abc", "")).isTrue();
        assertThat(Ascii.startsWithIgnoreCase("ab", "abc")).isFalse();
        assertThat(Ascii.startsWithIgnoreCase("ſcript", "script")).isFalse();
    }

    @Test
    void comparesRegionsIgnoringAsciiCaseOnly() {
        assertThat(Ascii.regionMatchesIgnoreCase("<!DOCTYPE html>", 2, "doctype"))
                .isTrue();
        assertThat(Ascii.regionMatchesIgnoreCase("<!DOCTYPE html>", 3, "doctype"))
                .isFalse();
        assertThat(Ascii.regionMatchesIgnoreCase("abc", 3, "")).isTrue();
    }

    @Test
    void doesNotMatchARegionPastTheEnd() {
        assertThat(Ascii.regionMatchesIgnoreCase("<!DOC", 2, "doctype")).isFalse();
        assertThat(Ascii.regionMatchesIgnoreCase("abc", 4, "")).isFalse();
        assertThat(Ascii.regionMatchesIgnoreCase("abc", Integer.MAX_VALUE, "c")).isFalse();
    }

    @Test
    void rejectsANegativeOffset() {
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> Ascii.regionMatchesIgnoreCase("abc", -1, ""))
                .withMessage("offset is negative: -1");
    }

    @Test
    void stripsAsciiWhitespace() {
        String text = "\t\n\f\r a  b \r\n";
        assertThat(Ascii.strip(text)).isEqualTo("a  b");
        assertThat(Ascii.stripLeading(text)).isEqualTo("a  b \r\n");
        assertThat(Ascii.stripTrailing(text)).isEqualTo("\t\n\f\r a  b");
        assertThat(Ascii.strip(CharBuffer.wrap(" a "))).isEqualTo("a");
    }

    @Test
    void stripsWhitespaceOnlyTextToNothing() {
        assertThat(Ascii.strip(" \t ")).isEmpty();
        assertThat(Ascii.stripLeading(" \t ")).isEmpty();
        assertThat(Ascii.stripTrailing(" \t ")).isEmpty();
        assertThat(Ascii.strip("")).isEmpty();
    }

    @Test
    void keepsOtherWhitespace() {
        // Line tabulation, no-break space and ideographic space.
        String text = "\u000Ba 　";
        assertThat(Ascii.strip(text)).isSameAs(text);
        assertThat(Ascii.stripLeading(text)).isSameAs(text);
        assertThat(Ascii.stripTrailing(text)).isSameAs(text);
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> Ascii.toLowerCase(null))
                .withMessage("text");
        assertThatNullPointerException()
                .isThrownBy(() -> Ascii.toUpperCase(null))
                .withMessage("text");
        assertThatNullPointerException()
                .isThrownBy(() -> Ascii.equalsIgnoreCase(null, "a"))
                .withMessage("a");
        assertThatNullPointerException()
                .isThrownBy(() -> Ascii.equalsIgnoreCase("a", null))
                .withMessage("b");
        assertThatNullPointerException()
                .isThrownBy(() -> Ascii.startsWithIgnoreCase(null, "a"))
                .withMessage("text");
        assertThatNullPointerException()
                .isThrownBy(() -> Ascii.startsWithIgnoreCase("a", null))
                .withMessage("prefix");
        assertThatNullPointerException()
                .isThrownBy(() -> Ascii.regionMatchesIgnoreCase(null, 0, "a"))
                .withMessage("text");
        assertThatNullPointerException()
                .isThrownBy(() -> Ascii.regionMatchesIgnoreCase("a", 0, null))
                .withMessage("other");
        assertThatNullPointerException().isThrownBy(() -> Ascii.strip(null)).withMessage("text");
        assertThatNullPointerException()
                .isThrownBy(() -> Ascii.stripLeading(null))
                .withMessage("text");
        assertThatNullPointerException()
                .isThrownBy(() -> Ascii.stripTrailing(null))
                .withMessage("text");
    }
}
