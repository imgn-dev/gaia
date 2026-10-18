package be.imgn.gaia.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class NamingConventionTest {

    static Stream<Arguments> pairs() {
        return Stream.of(NamingConvention.values())
                .flatMap(source -> Stream.of(NamingConvention.values()).map(target -> Arguments.of(source, target)));
    }

    private static String timeoutSeconds(NamingConvention convention) {
        return switch (convention) {
            case LOWER_CAMEL -> "timeoutSeconds";
            case UPPER_CAMEL -> "TimeoutSeconds";
            case LOWER_HYPHEN -> "timeout-seconds";
            case LOWER_UNDERSCORE -> "timeout_seconds";
            case UPPER_UNDERSCORE -> "TIMEOUT_SECONDS";
            case LOWER_DOT -> "timeout.seconds";
        };
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void convertsBetweenAnyTwoConventions(NamingConvention source, NamingConvention target) {
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
    void splitsLowerCamelWords(String text, String lowerHyphen) {
        assertThat(NamingConvention.LOWER_CAMEL.to(NamingConvention.LOWER_HYPHEN, text))
                .isEqualTo(lowerHyphen);
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
    void splitsUpperCamelWords(String text, String lowerHyphen) {
        assertThat(NamingConvention.UPPER_CAMEL.to(NamingConvention.LOWER_HYPHEN, text))
                .isEqualTo(lowerHyphen);
    }

    @Test
    void capitalizesOnlyTheFirstLetterOfACamelWord() {
        assertThat(NamingConvention.LOWER_CAMEL.to(NamingConvention.LOWER_CAMEL, "httpURL"))
                .isEqualTo("httpUrl");
        assertThat(NamingConvention.LOWER_HYPHEN.to(NamingConvention.UPPER_CAMEL, "http-url"))
                .isEqualTo("HttpUrl");
        assertThat(NamingConvention.LOWER_HYPHEN.to(NamingConvention.LOWER_CAMEL, "x-2y"))
                .isEqualTo("x2y");
    }

    @Test
    void returnsTextThatAlreadyFollowsTheTargetConvention() {
        assertThat(NamingConvention.UPPER_UNDERSCORE.to(NamingConvention.UPPER_UNDERSCORE, "MAX_SIZE_2"))
                .isEqualTo("MAX_SIZE_2");
        assertThat(NamingConvention.LOWER_DOT.to(NamingConvention.LOWER_DOT, "a.b.c"))
                .isEqualTo("a.b.c");
    }

    @Test
    void convertsUpperUnderscoreToLowerHyphen() {
        assertThat(NamingConvention.UPPER_UNDERSCORE.to(NamingConvention.LOWER_HYPHEN, "MAX_SIZE"))
                .isEqualTo("max-size");
        assertThat(NamingConvention.UPPER_UNDERSCORE.to(NamingConvention.LOWER_HYPHEN, "HTTP2"))
                .isEqualTo("http2");
    }

    @Test
    void ignoresTheDefaultLocale() {
        var locale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            assertThat(NamingConvention.UPPER_UNDERSCORE.to(NamingConvention.LOWER_HYPHEN, "TITLE_ID"))
                    .isEqualTo("title-id");
            assertThat(NamingConvention.LOWER_HYPHEN.to(NamingConvention.UPPER_UNDERSCORE, "title-id"))
                    .isEqualTo("TITLE_ID");
        } finally {
            Locale.setDefault(locale);
        }
    }

    @Test
    void convertsLettersOutsideAscii() {
        assertThat(NamingConvention.LOWER_CAMEL.to(NamingConvention.LOWER_UNDERSCORE, "größeÄnderung"))
                .isEqualTo("größe_änderung");
        assertThat(NamingConvention.LOWER_UNDERSCORE.to(NamingConvention.UPPER_UNDERSCORE, "größe_änderung"))
                .isEqualTo("GRÖßE_ÄNDERUNG");
        assertThat(NamingConvention.LOWER_HYPHEN.to(NamingConvention.UPPER_CAMEL, "ǆungla"))
                .isEqualTo("ǅungla");
        assertThat(NamingConvention.LOWER_CAMEL.to(NamingConvention.LOWER_HYPHEN, "名前Value"))
                .isEqualTo("名前-value");
        assertThat(NamingConvention.LOWER_HYPHEN.to(NamingConvention.LOWER_CAMEL, "a-𝒷"))
                .isEqualTo("a𝒷");
    }

    @ParameterizedTest
    @EnumSource(NamingConvention.class)
    void convertsToEachConventionDirectly(NamingConvention source) {
        var text = timeoutSeconds(source);

        assertThat(source.toLowerCamel(text)).isEqualTo("timeoutSeconds");
        assertThat(source.toUpperCamel(text)).isEqualTo("TimeoutSeconds");
        assertThat(source.toLowerHyphen(text)).isEqualTo("timeout-seconds");
        assertThat(source.toLowerUnderscore(text)).isEqualTo("timeout_seconds");
        assertThat(source.toUpperUnderscore(text)).isEqualTo("TIMEOUT_SECONDS");
        assertThat(source.toLowerDot(text)).isEqualTo("timeout.seconds");
    }

    @Test
    void rejectsTextThatDoesNotFollowItsConventionWhenConvertingDirectly() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NamingConvention.LOWER_HYPHEN.toLowerCamel("timeoutSeconds"))
                .withMessage("\"timeoutSeconds\" does not follow LOWER_HYPHEN");
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void convertsFromAnyConvention(NamingConvention source, NamingConvention target) {
        assertThat(target.from(timeoutSeconds(source))).isEqualTo(timeoutSeconds(target));
    }

    @ParameterizedTest
    @CsvSource({
        "name, name, Name, name, name, NAME, name",
        "NAME, name, Name, name, name, NAME, name",
        "utf8, utf8, Utf8, utf8, utf8, UTF8, utf8",
        "名前, 名前, 名前, 名前, 名前, 名前, 名前",
    })
    void convertsFromTextThatSeveralConventionsAccept(
            String text,
            String lowerCamel,
            String upperCamel,
            String lowerHyphen,
            String lowerUnderscore,
            String upperUnderscore,
            String dot) {
        assertThat(NamingConvention.LOWER_CAMEL.from(text)).isEqualTo(lowerCamel);
        assertThat(NamingConvention.UPPER_CAMEL.from(text)).isEqualTo(upperCamel);
        assertThat(NamingConvention.LOWER_HYPHEN.from(text)).isEqualTo(lowerHyphen);
        assertThat(NamingConvention.LOWER_UNDERSCORE.from(text)).isEqualTo(lowerUnderscore);
        assertThat(NamingConvention.UPPER_UNDERSCORE.from(text)).isEqualTo(upperUnderscore);
        assertThat(NamingConvention.LOWER_DOT.from(text)).isEqualTo(dot);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "2fa",
                "a b",
                "a$",
                "timeout-Seconds",
                "timeout-seconds_max",
                "a--b",
                "a-",
                "-a",
                "Max_Size",
                "Max_SIZE",
                "A.b",
            })
    void rejectsTextThatFollowsNoConvention(String text) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> NamingConvention.LOWER_HYPHEN.from(text))
                .withMessage("\"%s\" follows no naming convention", text);
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
        "LOWER_HYPHEN, Port",
        "LOWER_HYPHEN, max-Size",
        "LOWER_HYPHEN, a--b",
        "LOWER_HYPHEN, a-",
        "LOWER_HYPHEN, -a",
        "LOWER_HYPHEN, a_b",
        "LOWER_HYPHEN, 2fa",
        "LOWER_UNDERSCORE, a__b",
        "LOWER_UNDERSCORE, a-b",
        "UPPER_UNDERSCORE, Max_SIZE",
        "UPPER_UNDERSCORE, MAX__SIZE",
        "UPPER_UNDERSCORE, MAX_",
        "LOWER_DOT, a..b",
        "LOWER_DOT, A.b",
        "LOWER_DOT, a.",
    })
    void rejectsTextThatDoesNotFollowItsConvention(NamingConvention source, String text) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> source.to(NamingConvention.LOWER_HYPHEN, text))
                .withMessage("\"%s\" does not follow %s", text, source);
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNulls() {
        assertThatNullPointerException()
                .isThrownBy(() -> NamingConvention.LOWER_HYPHEN.to(null, "a"))
                .withMessage("target");
        assertThatNullPointerException()
                .isThrownBy(() -> NamingConvention.LOWER_HYPHEN.to(NamingConvention.LOWER_UNDERSCORE, null))
                .withMessage("text");
        assertThatNullPointerException()
                .isThrownBy(() -> NamingConvention.LOWER_HYPHEN.from(null))
                .withMessage("text");
    }
}
