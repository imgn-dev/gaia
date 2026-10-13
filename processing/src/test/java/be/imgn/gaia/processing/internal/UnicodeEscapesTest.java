package be.imgn.gaia.processing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class UnicodeEscapesTest {

    private static final int HEX_DIGITS = 4;
    private static final int HEX = 16;

    // Translates the Unicode escapes of a source as javac does (JLS 3.3): a backslash starts an escape only after an
    // even number of contiguous backslashes, and the character an escape gives never starts another one.
    private static String translate(String source) {
        StringBuilder translated = new StringBuilder();
        int backslashes = 0;
        int index = 0;
        while (index < source.length()) {
            char character = source.charAt(index);
            boolean escape = character == '\\'
                    && backslashes % 2 == 0
                    && index + 1 < source.length()
                    && source.charAt(index + 1) == 'u';
            if (escape) {
                int digits = index + 1;
                while (source.charAt(digits) == 'u') {
                    digits++;
                }
                translated.append((char) Integer.parseInt(source.substring(digits, digits + HEX_DIGITS), HEX));
                index = digits + HEX_DIGITS;
                backslashes = 0;
            } else {
                translated.append(character);
                backslashes = character == '\\' ? backslashes + 1 : 0;
                index++;
            }
        }
        return translated.toString();
    }

    @Test
    void returnsAnAsciiSourceAsItIs() {
        String source = "class A { String s = \"\\u00e9 \\\\ \\n\"; char c = '\\u007f'; }\u007f";

        assertThat(UnicodeEscapes.escapeNonAscii(source)).isSameAs(source);
    }

    @Test
    void escapesEachCharacterAfterAsciiWithFourLowerCaseDigits() {
        assertThat(UnicodeEscapes.escapeNonAscii("caf\u00e9 \u0080\u00ff\u65e5\uffff"))
                .isEqualTo("caf\\u00e9 \\u0080\\u00ff\\u65e5\\uffff");
    }

    @Test
    void escapesEachHalfOfASupplementaryCharacter() {
        assertThat(UnicodeEscapes.escapeNonAscii("\uD83D\uDE42")).isEqualTo("\\ud83d\\ude42");
    }

    @Test
    void escapesTheLastOfAnOddNumberOfBackslashesBeforeAnEscapedCharacter() {
        assertThat(UnicodeEscapes.escapeNonAscii("\\\u00e9")).isEqualTo("\\u005c\\u00e9");
        assertThat(UnicodeEscapes.escapeNonAscii("\\\\\\\u00e9")).isEqualTo("\\\\\\u005c\\u00e9");
    }

    @Test
    void leavesAnEvenNumberOfBackslashesBeforeAnEscapedCharacterAlone() {
        assertThat(UnicodeEscapes.escapeNonAscii("\\\\\u00e9")).isEqualTo("\\\\\\u00e9");
        assertThat(UnicodeEscapes.escapeNonAscii("\\\\\\\\\u00e9")).isEqualTo("\\\\\\\\\\u00e9");
    }

    @Test
    void keepsTheEscapesAlreadyInTheSource() {
        assertThat(UnicodeEscapes.escapeNonAscii("\\u00e9\u00e9\\uuu0041")).isEqualTo("\\u00e9\\u00e9\\uuu0041");
    }

    @Test
    void writesASourceInAsciiThatTranslatesBackToIt() {
        List<String> sources = List.of(
                "caf\u00e9",
                "\\\u00e9",
                "\\\\\u00e9",
                "\\\\\\\u00e9",
                "\u00e9\\",
                "\u00e9\\u0041",
                "\\\u00e9\\u0041",
                "\\u005c\u00e9",
                "a\\\uD83D\uDE42b",
                "\\\\\\\\\\\u65e5\\\u6708",
                "\"\\\\\u00e9\" // \\\u00e9 /** \\\\\\\u00e9 */");

        for (String source : sources) {
            String escaped = UnicodeEscapes.escapeNonAscii(source);

            assertThat(escaped).as(source).matches("\\p{ASCII}*");
            assertThat(translate(escaped)).as(source).isEqualTo(translate(source));
        }
    }
}
