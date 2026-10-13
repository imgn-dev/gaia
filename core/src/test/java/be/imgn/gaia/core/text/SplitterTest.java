package be.imgn.gaia.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.CharBuffer;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SplitterTest {

    static Stream<Arguments> separators() {
        return Stream.of(
                Arguments.of(Splitter.on(':'), "a:b::c:", List.of("a", "b", "", "c", "")),
                Arguments.of(Splitter.on("::"), "a::b::::c", List.of("a", "b", "", "c")),
                Arguments.of(Splitter.on(Pattern.compile(",\\s*")), "a, b,c,  d", List.of("a", "b", "c", "d")),
                Arguments.of(Splitter.onPattern("\\s+"), "a  b\tc", List.of("a", "b", "c")),
                Arguments.of(Splitter.on(':'), "", List.of("")),
                Arguments.of(Splitter.on(':'), "abc", List.of("abc")),
                Arguments.of(Splitter.on(':'), ":", List.of("", "")));
    }

    @ParameterizedTest
    @MethodSource("separators")
    void splitsAroundTheSeparator(Splitter splitter, String text, List<String> parts) {
        assertThat(splitter.split(text)).isEqualTo(parts);
        assertThat(splitter.splitToStream(text)).containsExactlyElementsOf(parts);
    }

    @Test
    void treatsAStringSeparatorLiterally() {
        assertThat(Splitter.on(".").split("a.b.c")).containsExactly("a", "b", "c");
        assertThat(Splitter.on("|").split("a|b")).containsExactly("a", "b");
    }

    @Test
    void ignoresZeroLengthMatches() {
        assertThat(Splitter.onPattern("(?=b)|,").split("ab,c")).containsExactly("ab", "c");
    }

    @Test
    void returnsAnUnmodifiableList() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> Splitter.on(',').split("a,b").add("c"));
    }

    @Test
    void acceptsAnyCharSequence() {
        assertThat(Splitter.on(',').split(CharBuffer.wrap("a,b"))).containsExactly("a", "b");
    }

    @Nested
    class Options {

        @Test
        void trimsEachPart() {
            assertThat(Splitter.on(',').trimResults().split(" a ,\tb\n, ,c")).containsExactly("a", "b", "", "c");
        }

        @Test
        void omitsEmptyParts() {
            assertThat(Splitter.on(',').omitEmptyStrings().split(",a,,b,")).containsExactly("a", "b");
            assertThat(Splitter.on(',').omitEmptyStrings().split("")).isEmpty();
        }

        @Test
        void omitsPartsLeftEmptyByTrimming() {
            assertThat(Splitter.on(',').trimResults().omitEmptyStrings().split(" a, ,b ,, c "))
                    .containsExactly("a", "b", "c");
        }

        @Test
        void keepsTheRestOfTheTextInTheLastPart() {
            assertThat(Splitter.on(',').limit(2).split("a,b,c")).containsExactly("a", "b,c");
            assertThat(Splitter.on(',').limit(1).split("a,b")).containsExactly("a,b");
            assertThat(Splitter.on(',').limit(5).split("a,b")).containsExactly("a", "b");
        }

        @Test
        void doesNotCountOmittedPartsTowardsTheLimit() {
            assertThat(Splitter.on(',').omitEmptyStrings().limit(3).split("a,,,b,,,c,d"))
                    .containsExactly("a", "b", "c,d");
        }

        @Test
        void trimsTheLastPartToo() {
            assertThat(Splitter.on(',').trimResults().limit(2).split(" a , b , c "))
                    .containsExactly("a", "b , c");
        }

        @Test
        void leavesTheOriginalSplitterUnchanged() {
            Splitter splitter = Splitter.on(',');

            splitter.trimResults();
            splitter.omitEmptyStrings();
            splitter.limit(1);

            assertThat(splitter.split(" a,,b")).containsExactly(" a", "", "b");
        }
    }

    @Nested
    class Rejects {

        @Test
        void anEmptySeparator() {
            assertThatIllegalArgumentException().isThrownBy(() -> Splitter.on(""));
        }

        @Test
        void aPatternMatchingTheEmptyString() {
            assertThatIllegalArgumentException().isThrownBy(() -> Splitter.onPattern("a*"));
            assertThatIllegalArgumentException().isThrownBy(() -> Splitter.on(Pattern.compile("")));
        }

        @Test
        void anInvalidPattern() {
            assertThatExceptionOfType(PatternSyntaxException.class).isThrownBy(() -> Splitter.onPattern("("));
        }

        @Test
        void aLimitThatIsNotPositive() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Splitter.on(',').limit(0));
        }
    }
}
