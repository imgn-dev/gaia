package be.imgn.gaia.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class CaseTest {

    static Stream<Arguments> pairs() {
        return Stream.of(Case.values())
                .flatMap(source -> Stream.of(Case.values()).map(target -> Arguments.of(source, target)));
    }

    private static String timeoutSeconds(Case textCase) {
        return switch (textCase) {
            case LOWER_CAMEL -> "timeoutSeconds";
            case UPPER_CAMEL -> "TimeoutSeconds";
            case KEBAB -> "timeout-seconds";
            case SNAKE -> "timeout_seconds";
            case UPPER_SNAKE -> "TIMEOUT_SECONDS";
            case DOT -> "timeout.seconds";
        };
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void convertsBetweenAnyTwoCases(Case source, Case target) {
        assertThat(source.to(target, timeoutSeconds(source))).isEqualTo(timeoutSeconds(target));
    }

    @ParameterizedTest
    @CsvSource({
        "port, port",
        "timeoutSeconds, timeout-seconds",
        "httpURL, http-url",
        "httpURLPath, http-url-path",
        "urlPath, url-path",
        "ipv4Address, ipv4-address",
        "utf8, utf8",
        "version2, version2",
        "a1b2C3, a1b2-c3",
        "aB, a-b",
        "aBC, a-bc",
    })
    void splitsLowerCamelWords(String text, String kebab) {
        assertThat(Case.LOWER_CAMEL.to(Case.KEBAB, text)).isEqualTo(kebab);
    }

    @ParameterizedTest
    @CsvSource({
        "Port, port",
        "XMLParser, xml-parser",
        "URL, url",
        "URL2, url2",
        "HTTP2Server, http2-server",
        "IPv4Address, i-pv4-address",
        "A, a",
    })
    void splitsUpperCamelWords(String text, String kebab) {
        assertThat(Case.UPPER_CAMEL.to(Case.KEBAB, text)).isEqualTo(kebab);
    }

    @Test
    void capitalizesOnlyTheFirstLetterOfACamelWord() {
        assertThat(Case.LOWER_CAMEL.to(Case.LOWER_CAMEL, "httpURL")).isEqualTo("httpUrl");
        assertThat(Case.KEBAB.to(Case.UPPER_CAMEL, "http-url")).isEqualTo("HttpUrl");
        assertThat(Case.KEBAB.to(Case.LOWER_CAMEL, "x-2y")).isEqualTo("x2y");
    }

    @Test
    void returnsTextAlreadyInTheTargetCase() {
        assertThat(Case.UPPER_SNAKE.to(Case.UPPER_SNAKE, "MAX_SIZE_2")).isEqualTo("MAX_SIZE_2");
        assertThat(Case.DOT.to(Case.DOT, "a.b.c")).isEqualTo("a.b.c");
    }

    @Test
    void convertsUpperSnakeToKebab() {
        assertThat(Case.UPPER_SNAKE.to(Case.KEBAB, "MAX_SIZE")).isEqualTo("max-size");
        assertThat(Case.UPPER_SNAKE.to(Case.KEBAB, "HTTP2")).isEqualTo("http2");
    }

    @Test
    void ignoresTheDefaultLocale() {
        var locale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            assertThat(Case.UPPER_SNAKE.to(Case.KEBAB, "TITLE_ID")).isEqualTo("title-id");
            assertThat(Case.KEBAB.to(Case.UPPER_SNAKE, "title-id")).isEqualTo("TITLE_ID");
        } finally {
            Locale.setDefault(locale);
        }
    }

    @Test
    void convertsLettersOutsideAscii() {
        assertThat(Case.LOWER_CAMEL.to(Case.SNAKE, "größeÄnderung")).isEqualTo("größe_änderung");
        assertThat(Case.SNAKE.to(Case.UPPER_SNAKE, "größe_änderung")).isEqualTo("GRÖßE_ÄNDERUNG");
        assertThat(Case.KEBAB.to(Case.UPPER_CAMEL, "ǆungla")).isEqualTo("ǅungla");
        assertThat(Case.LOWER_CAMEL.to(Case.KEBAB, "名前Value")).isEqualTo("名前-value");
        assertThat(Case.KEBAB.to(Case.LOWER_CAMEL, "a-𝒷")).isEqualTo("a𝒷");
    }

    @ParameterizedTest
    @CsvSource({
        "LOWER_CAMEL, ''",
        "LOWER_CAMEL, Port",
        "LOWER_CAMEL, max_size",
        "LOWER_CAMEL, max-size",
        "LOWER_CAMEL, 2fa",
        "LOWER_CAMEL, 'a b'",
        "LOWER_CAMEL, a$",
        "UPPER_CAMEL, port",
        "UPPER_CAMEL, Max_Size",
        "UPPER_CAMEL, 名前",
        "KEBAB, Port",
        "KEBAB, max-Size",
        "KEBAB, a--b",
        "KEBAB, a-",
        "KEBAB, -a",
        "KEBAB, a_b",
        "KEBAB, 2fa",
        "SNAKE, a__b",
        "SNAKE, a-b",
        "UPPER_SNAKE, Max_SIZE",
        "UPPER_SNAKE, MAX__SIZE",
        "UPPER_SNAKE, MAX_",
        "DOT, a..b",
        "DOT, A.b",
        "DOT, a.",
    })
    void rejectsTextNotWrittenInItsCase(Case source, String text) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> source.to(Case.KEBAB, text))
                .withMessage("\"%s\" is not %s text", text, source);
    }
}
