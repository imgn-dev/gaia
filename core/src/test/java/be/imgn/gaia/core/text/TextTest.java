package be.imgn.gaia.core.text;

import static be.imgn.gaia.core.text.Text.first;
import static be.imgn.gaia.core.text.Text.last;
import static be.imgn.gaia.core.text.Text.prefix;
import static be.imgn.gaia.core.text.Text.suffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.CharBuffer;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void selectsTheFirstOrLastOccurrence() {
        assertThat(first('.').removeFrom("www.example.com")).isEqualTo("wwwexample.com");
        assertThat(last('.').removeFrom("www.example.com")).isEqualTo("www.examplecom");
        assertThat(first("::").replaceFrom("a::b::c", "/")).isEqualTo("a/b::c");
        assertThat(last("::").replaceFrom("a::b::c", "/")).isEqualTo("a::b/c");
        assertThat(last("aa").extractFrom("baaa")).hasValue("aa");
        assertThat(last("aa").removeFrom("baaa")).isEqualTo("ba");
    }

    @Test
    void selectsAnAffixOnlyAtItsEnd() {
        assertThat(prefix("www.").removeFrom("www.example.com")).isEqualTo("example.com");
        assertThat(prefix("www.").removeFrom("a.www.example.com")).isEqualTo("a.www.example.com");
        assertThat(suffix(".jar").removeFrom("gaia.jar")).isEqualTo("gaia");
        assertThat(suffix(".jar").removeFrom("gaia.jar.sha1")).isEqualTo("gaia.jar.sha1");
        assertThat(prefix('a').extractFrom("a")).hasValue("a");
        assertThat(suffix('a').replaceFrom("ba", "c")).isEqualTo("bc");
    }

    @Test
    void leavesTheTextUnchangedWithoutAnOccurrence() {
        assertThat(first('@').extractFrom("example.com")).isEmpty();
        assertThat(first('@').removeFrom("example.com")).isEqualTo("example.com");
        assertThat(last("@").replaceFrom("", "x")).isEmpty();
        assertThat(prefix("abc").extractFrom("ab")).isEmpty();
        assertThat(suffix("abc").extractFrom("bc")).isEmpty();
    }

    @Test
    void replacesLiterally() {
        assertThat(first(Pattern.compile("(\\d+)")).replaceFrom("v12", "$1\\")).isEqualTo("v$1\\");
    }

    @Test
    void acceptsAnyCharSequence() {
        assertThat(first('b').removeFrom(CharBuffer.wrap("abc"))).isEqualTo("ac");
        assertThat(prefix('/').addToIfAbsent(CharBuffer.wrap("a"))).isEqualTo("/a");
    }

    @Test
    void selectsTheFirstOccurrenceThatSatisfiesTheConditions() {
        var gmail = first('@').followedBy("gmail.com");

        assertThat(gmail.replaceFrom("x@y.com, ann@gmail.com", " at ")).isEqualTo("x@y.com, ann at gmail.com");
        assertThat(first('@').notFollowedBy("gmail.com").replaceFrom("ann@gmail.com, x@y.com", " at "))
                .isEqualTo("ann@gmail.com, x at y.com");
        assertThat(first('@').precededBy("ann").replaceFrom("bob@x.com, ann@y.com", " at "))
                .isEqualTo("bob@x.com, ann at y.com");
        assertThat(first(':').notPrecededBy("\\").replaceFrom("a\\:b:c", "=")).isEqualTo("a\\:b=c");
        assertThat(first("aa").followedBy("b").replaceFrom("aaab", "-")).isEqualTo("a-b");
        assertThat(gmail.extractFrom("ann@yahoo.com")).isEmpty();
    }

    @Test
    void selectsTheLastOccurrenceThatSatisfiesTheConditions() {
        assertThat(last('.').notFollowedBy("gz").removeFrom("a.tar.gz")).isEqualTo("atar.gz");
        assertThat(last('.').precededBy("a").followedBy("t").removeFrom("a.tar.gz"))
                .isEqualTo("atar.gz");
        assertThat(last('.').followedBy("zip").removeFrom("a.tar.gz")).isEqualTo("a.tar.gz");
    }

    @Test
    void combinesConditions() {
        var pattern = first('-').precededBy("a").notFollowedBy("c");

        assertThat(pattern.replaceFrom("a-c b-d a-d", "+")).isEqualTo("a-c b-d a+d");
    }

    @Test
    void checksConditionsOnAnAffix() {
        assertThat(prefix("http").notFollowedBy("s").replaceFrom("http://a", "https"))
                .isEqualTo("https://a");
        assertThat(prefix("http").notFollowedBy("s").replaceFrom("https://a", "https"))
                .isEqualTo("https://a");
        assertThat(suffix(".gz").precededBy(".tar").removeFrom("a.tar.gz")).isEqualTo("a.tar");
        assertThat(prefix("a").precededBy("x").extractFrom("a")).isEmpty();
        assertThat(suffix("a").followedBy("x").extractFrom("a")).isEmpty();
        assertThat(prefix("a").followedBy("b")).isNotInstanceOf(Text.Affix.class);
    }

    @Test
    void selectsTheFirstOrLastMatchOfARegex() {
        var digits = Pattern.compile("\\d+");

        assertThat(first(digits).extractFrom("v12.3")).hasValue("12");
        assertThat(last(digits).extractFrom("a12")).hasValue("12");
        assertThat(last(digits).removeFrom("v1.2.10")).isEqualTo("v1.2.");
        assertThat(first(digits).extractFrom("v")).isEmpty();
        assertThat(last(digits).extractFrom("v")).isEmpty();
    }

    @Test
    void ignoresMatchesOfLengthZero() {
        var pattern = Pattern.compile("(?<=a)|c");

        assertThat(first(pattern).extractFrom("abc")).hasValue("c");
        assertThat(last(pattern).extractFrom("abca")).hasValue("c");
    }

    @Test
    void skipsMatchesOfARegexThatFailTheConditions() {
        var digits = Pattern.compile("\\d+");

        assertThat(first(digits).followedBy("x").extractFrom("12 34x 56x")).hasValue("34");
        assertThat(last(digits).followedBy("x").extractFrom("12x 34x 56")).hasValue("34");
        assertThat(first(digits).followedBy("5").extractFrom("1235")).isEmpty();
        assertThat(first(digits).precededBy("v").extractFrom("1v23")).hasValue("23");
        assertThat(first(Pattern.compile("[a-z]+")).followedBy("1").extractFrom("ab1"))
                .hasValue("ab");
        assertThat(first(Pattern.compile("[a-z]+")).precededBy("a").extractFrom("abc"))
                .hasValue("bc");
    }

    @Test
    void addsAnAffixIfAbsent() {
        var https = prefix("https://");

        assertThat(https.addToIfAbsent("example.com")).isEqualTo("https://example.com");
        assertThat(https.addToIfAbsent("https://example.com")).isEqualTo("https://example.com");
        assertThat(suffix('/').addToIfAbsent("/api")).isEqualTo("/api/");
        assertThat(suffix('/').addToIfAbsent("/api/")).isEqualTo("/api/");
        assertThat(suffix('/').addToIfAbsent("")).isEqualTo("/");
    }

    @Test
    void isTheCharSequenceOfItsLiteral() {
        var affix = prefix("https");

        assertThat(affix.length()).isEqualTo(5);
        assertThat(affix.charAt(1)).isEqualTo('t');
        assertThat(affix.subSequence(1, 3)).isEqualTo("tt");
        assertThat(affix).hasToString("https");
        assertThat(String.join("", affix, "://")).isEqualTo("https://");
    }

    @Test
    void comparesAffixesByKindAndLiteral() {
        assertThat(prefix('a')).isEqualTo(prefix("a")).hasSameHashCodeAs(prefix("a"));
        assertThat(suffix('a')).isEqualTo(suffix("a")).hasSameHashCodeAs(suffix("a"));
        assertThat(prefix('a')).isNotEqualTo(suffix('a')).isNotEqualTo(prefix('b'));
        assertThat((CharSequence) prefix("a")).isNotEqualTo("a");
    }

    @Test
    void rejectsEmptyLiterals() {
        assertThatIllegalArgumentException().isThrownBy(() -> first("")).withMessage("The literal must not be empty");
        assertThatIllegalArgumentException().isThrownBy(() -> last(""));
        assertThatIllegalArgumentException().isThrownBy(() -> prefix(""));
        assertThatIllegalArgumentException().isThrownBy(() -> suffix(""));
        assertThatIllegalArgumentException().isThrownBy(() -> first('a').followedBy(""));
        assertThatIllegalArgumentException().isThrownBy(() -> prefix('a').notFollowedBy(""));
        assertThatIllegalArgumentException().isThrownBy(() -> last('a').precededBy(""));
        assertThatIllegalArgumentException().isThrownBy(() -> suffix('a').notPrecededBy(""));
    }

    @Test
    void rejectsARegexThatMatchesTheEmptyString() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> first(Pattern.compile("a*")))
                .withMessage("The regex must not match the empty string: a*");
        assertThatIllegalArgumentException().isThrownBy(() -> last(Pattern.compile("a?")));
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() -> first((String) null)).withMessage("literal");
        assertThatNullPointerException().isThrownBy(() -> last((Pattern) null)).withMessage("regex");
        assertThatNullPointerException().isThrownBy(() -> prefix(null)).withMessage("literal");
        assertThatNullPointerException()
                .isThrownBy(() -> first('a').followedBy(null))
                .withMessage("literal");
        assertThatNullPointerException()
                .isThrownBy(() -> first('a').extractFrom(null))
                .withMessage("text");
        assertThatNullPointerException()
                .isThrownBy(() -> prefix('a').removeFrom(null))
                .withMessage("text");
        assertThatNullPointerException()
                .isThrownBy(() -> first('a').replaceFrom("a", null))
                .withMessage("replacement");
        assertThatNullPointerException()
                .isThrownBy(() -> suffix('a').addToIfAbsent(null))
                .withMessage("text");
    }
}
