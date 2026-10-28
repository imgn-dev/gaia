package be.imgn.gaia.core.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class VersionTest {

    @Test
    void parsesEveryPart() {
        Version version = Version.parse("1.4.0-rc.1+build.05");

        assertThat(version.major()).isEqualTo(1);
        assertThat(version.minor()).isEqualTo(4);
        assertThat(version.patch()).isZero();
        assertThat(version.preRelease()).containsExactly("rc", "1");
        assertThat(version.build()).containsExactly("build", "05");
        assertThat(version.isPreRelease()).isTrue();
        assertThat(version).hasToString("1.4.0-rc.1+build.05");
    }

    @Test
    void parsesARelease() {
        Version version = Version.parse("10.20.30");

        assertThat(version).isEqualTo(Version.of(10, 20, 30));
        assertThat(version.preRelease()).isEmpty();
        assertThat(version.build()).isEmpty();
        assertThat(version.isPreRelease()).isFalse();
        assertThat(version).hasToString("10.20.30");
    }

    // The valid versions of the test list of the regular expression that semver.org suggests, which fit in a long.
    @Test
    void parsesTheVersionsSemanticVersioningAllows() {
        for (String text : List.of(
                "0.0.4",
                "1.2.3",
                "10.20.30",
                "1.1.2-prerelease+meta",
                "1.1.2+meta",
                "1.1.2+meta-valid",
                "1.0.0-alpha",
                "1.0.0-beta",
                "1.0.0-alpha.beta",
                "1.0.0-alpha.beta.1",
                "1.0.0-alpha.1",
                "1.0.0-alpha0.valid",
                "1.0.0-alpha.0valid",
                "1.0.0-alpha-a.b-c-somethinglong+build.1-aef.1-its-okay",
                "1.0.0-rc.1+build.1",
                "2.0.0-rc.1+build.123",
                "1.2.3-beta",
                "10.2.3-DEV-SNAPSHOT",
                "1.2.3-SNAPSHOT-123",
                "2.0.0+build.1848",
                "2.0.1-alpha.1227",
                "1.0.0-alpha+beta",
                "1.2.3----RC-SNAPSHOT.12.9.1--.12+788",
                "1.2.3----R-S.12.9.1--.12+meta",
                "1.2.3----RC-SNAPSHOT.12.9.1--.12",
                "1.0.0+0.build.1-rc.10000aaa-kk-0.1",
                "1.0.0-0A.is.legal",
                "9223372036854775807.9223372036854775807.9223372036854775807")) {
            assertThat(Version.parse(text)).as(text).hasToString(text);
            assertThat(Version.tryParse(text)).as(text).contains(Version.parse(text));
        }
    }

    // The invalid versions of the test list of the regular expression that semver.org suggests.
    @Test
    void rejectsWhatSemanticVersioningDoesNotAllow() {
        for (String text : List.of(
                "1",
                "1.2",
                "1.2.3-0123",
                "1.2.3-0123.0123",
                "1.1.2+.123",
                "+invalid",
                "-invalid",
                "-invalid+invalid",
                "-invalid.01",
                "alpha",
                "alpha.beta",
                "alpha.beta.1",
                "alpha.1",
                "alpha+beta",
                "alpha_beta",
                "alpha.",
                "alpha..",
                "beta",
                "1.0.0-alpha_beta",
                "-alpha.",
                "1.0.0-alpha..",
                "1.0.0-alpha..1",
                "1.0.0-alpha...1",
                "01.1.1",
                "1.01.1",
                "1.1.01",
                "1.2.3.DEV",
                "1.2-SNAPSHOT",
                "1.2.31.2.3----RC-SNAPSHOT.12.09.1--..12+788",
                "1.2-RC-SNAPSHOT",
                "-1.0.3-gamma+b7718",
                "+justmeta",
                "9.8.7+meta+meta",
                "9.8.7-whatever+meta+meta",
                "99999999999999999999999.999999999999999999.99999999999999999----RC-SNAPSHOT.12.09.1--..12",
                "",
                "v1.2.3",
                " 1.2.3",
                "1.2.3 ",
                "1.2.3-",
                "1.2.3+",
                "1.2.3-é",
                "١.2.3")) {
            assertThatIllegalArgumentException()
                    .as(text)
                    .isThrownBy(() -> Version.parse(text))
                    .withMessageStartingWith("Not a semantic version, ");
            assertThat(Version.tryParse(text)).as(text).isEmpty();
        }
    }

    @Test
    void namesWhatIsWrongWithTheText() {
        assertParseFails("1.2", "it needs a major, a minor and a patch version, separated by dots: \"1.2\"");
        assertParseFails("1..3", "the minor version is empty: \"1..3\"");
        assertParseFails("1.2.x", "the patch version is not a number: \"1.2.x\"");
        assertParseFails("01.2.3", "the major version has a leading zero: \"01.2.3\"");
        assertParseFails(
                "1.9223372036854775808.3",
                "the minor version is larger than 9223372036854775807: \"1.9223372036854775808.3\"");
        assertParseFails("1.2.3-a..b", "a pre-release identifier is empty: \"1.2.3-a..b\"");
        assertParseFails(
                "1.2.3-a_b",
                "the pre-release identifier \"a_b\" has a character other than an ASCII letter, digit or hyphen:"
                        + " \"1.2.3-a_b\"");
        assertParseFails("1.2.3-01", "the numeric pre-release identifier \"01\" has a leading zero: \"1.2.3-01\"");
        assertParseFails("1.2.3+", "a build identifier is empty: \"1.2.3+\"");
        assertParseFails(
                "1.2.3+a+b",
                "the build identifier \"a+b\" has a character other than an ASCII letter, digit or hyphen:"
                        + " \"1.2.3+a+b\"");
        assertParseFails("1.2.3\n", "the patch version is not a number: \"1.2.3\\u000a\"");
    }

    @Test
    void acceptsLeadingZerosInBuildIdentifiers() {
        assertThat(Version.parse("1.0.0+001").build()).containsExactly("001");
    }

    @Test
    void ordersByPrecedence() {
        // The examples of the specification, and numeric identifiers longer than a long.
        assertOrdered(
                "1.0.0-alpha",
                "1.0.0-alpha.1",
                "1.0.0-alpha.9223372036854775808",
                "1.0.0-alpha.99999999999999999999",
                "1.0.0-alpha.beta",
                "1.0.0-beta",
                "1.0.0-beta.2",
                "1.0.0-beta.11",
                "1.0.0-rc.1",
                "1.0.0",
                "2.0.0",
                "2.1.0",
                "2.1.1",
                "2.1.10",
                "2.10.0",
                "10.0.0");
    }

    @Test
    void comparesAlphanumericIdentifiersInAsciiOrder() {
        assertOrdered("1.0.0-0", "1.0.0-A", "1.0.0-Z", "1.0.0-a", "1.0.0-a-b", "1.0.0-ab", "1.0.0-b");
    }

    @Test
    void ignoresBuildMetadataInPrecedence() {
        Version first = Version.parse("1.0.0+a");
        Version second = Version.parse("1.0.0+b");

        assertThat(first.compareTo(second)).isZero();
        assertThat(first.compareTo(Version.of(1, 0, 0))).isZero();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void isEqualToTheSameVersionOnly() {
        assertThat(Version.parse("1.0.0-rc.1+b"))
                .isEqualTo(new Version(1, 0, 0, List.of("rc", "1"), List.of("b")))
                .hasSameHashCodeAs(new Version(1, 0, 0, List.of("rc", "1"), List.of("b")))
                .isNotEqualTo(Version.parse("1.0.0-rc.1"))
                .isNotEqualTo(Version.parse("1.0.0-rc.2+b"));
    }

    @Test
    void copiesTheIdentifiers() {
        List<String> preRelease = new ArrayList<>(List.of("rc"));
        Version version = new Version(1, 0, 0, preRelease, List.of());
        preRelease.add("1");

        assertThat(version.preRelease()).containsExactly("rc");
    }

    @Test
    void rejectsNegativeNumbers() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Version.of(-1, 0, 0))
                .withMessage("Not a semantic version, the major version is negative: -1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Version.of(0, -1, 0))
                .withMessage("Not a semantic version, the minor version is negative: -1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Version.of(0, 0, -1))
                .withMessage("Not a semantic version, the patch version is negative: -1");
    }

    @Test
    void rejectsInvalidIdentifiers() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Version(1, 0, 0, List.of("01"), List.of()))
                .withMessage("Not a semantic version, the numeric pre-release identifier \"01\" has a leading zero");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Version(1, 0, 0, List.of(), List.of("")))
                .withMessage("Not a semantic version, a build identifier is empty");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Version(1, 0, 0, List.of("a.b"), List.of()))
                .withMessage("Not a semantic version, the pre-release identifier \"a.b\" has a character other than an"
                        + " ASCII letter, digit or hyphen");
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNulls() {
        assertThatNullPointerException().isThrownBy(() -> Version.parse(null)).withMessage("text");
        assertThatNullPointerException()
                .isThrownBy(() -> Version.tryParse(null))
                .withMessage("text");
        assertThatNullPointerException()
                .isThrownBy(() -> Version.of(1, 0, 0).compareTo(null))
                .withMessage("other");
        assertThatNullPointerException()
                .isThrownBy(() -> new Version(1, 0, 0, null, List.of()))
                .withMessage("preRelease");
        assertThatNullPointerException()
                .isThrownBy(() -> new Version(1, 0, 0, List.of(), null))
                .withMessage("build");
        assertThatNullPointerException()
                .isThrownBy(() -> new Version(1, 0, 0, Arrays.asList("rc", null), List.of()))
                .withMessage("preRelease identifier");
    }

    private static void assertParseFails(String text, String reason) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Version.parse(text))
                .withMessage("Not a semantic version, " + reason);
    }

    // Each version has a lower precedence than the next, and sorting them in any order gives them back.
    private static void assertOrdered(String... texts) {
        List<Version> versions = Arrays.stream(texts).map(Version::parse).toList();
        for (int i = 0; i < versions.size(); i++) {
            for (int j = 0; j < versions.size(); j++) {
                assertThat(Integer.signum(versions.get(i).compareTo(versions.get(j))))
                        .as("%s compared with %s", versions.get(i), versions.get(j))
                        .isEqualTo(Integer.compare(i, j));
            }
        }
        List<Version> shuffled = new ArrayList<>(versions);
        Collections.shuffle(shuffled, new Random(42));
        Collections.sort(shuffled);
        assertThat(shuffled).isEqualTo(versions);
    }
}
