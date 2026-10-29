package be.imgn.gaia.core.base;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.text.Splitter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A version number as <a href="https://semver.org/spec/v2.0.0.html">Semantic Versioning 2.0.0</a> defines it, such as
 * {@code 1.4.0-rc.1+build.5}: a major, minor and patch version, then optional pre-release identifiers after {@code -},
 * then optional build metadata identifiers after {@code +}.
 *
 * <pre>{@code
 * Version version = Version.parse("1.4.0-rc.1+build.5");
 * version.minor();                                  // 4
 * version.preRelease();                             // [rc, 1]
 * version.compareTo(Version.parse("1.4.0")) < 0;    // true: a pre-release comes before its release
 * }</pre>
 *
 * <p>The major, minor and patch versions are numbers without leading zeros. Identifiers are made of ASCII letters,
 * digits and hyphens, and are not empty. A numeric pre-release identifier has no leading zero; a build identifier may
 * have one. Semantic Versioning sets no limit on numbers; here the major, minor and patch versions are at most
 * {@link Long#MAX_VALUE}, while a numeric pre-release identifier, kept as text, has no limit.
 *
 * <p>Versions are ordered by precedence, as Semantic Versioning defines it: by major, minor and patch version, as
 * numbers; then a pre-release before the version without pre-release identifiers; then by pre-release identifiers, from
 * left to right, numeric ones compared as numbers and before alphanumeric ones, which are compared in ASCII order, and
 * the version with more identifiers last if all the others are equal. Build metadata is ignored, so two versions that
 * differ only in their build metadata have the same precedence: {@link #compareTo(Version)} returns {@code 0} for them,
 * while they are not {@linkplain #equals(Object) equal}. This natural ordering is therefore inconsistent with
 * {@code equals}: a sorted set or map keeps only one of them.
 *
 * @param major the major version
 * @param minor the minor version
 * @param patch the patch version
 * @param preRelease the pre-release identifiers, such as {@code [rc, 1]}; empty if the version is not a pre-release
 * @param build the build metadata identifiers, such as {@code [build, 5]}; empty if there is no build metadata
 * @since 0.1.2
 */
public record Version(long major, long minor, long patch, List<String> preRelease, List<String> build)
        implements Comparable<Version> {

    private static final Splitter DOT = Splitter.on('.');
    private static final String MAX_NUMBER = Long.toString(Long.MAX_VALUE);

    /**
     * Creates a version. The lists are copied.
     *
     * @throws IllegalArgumentException if {@code major}, {@code minor} or {@code patch} is negative, or if an
     *     identifier is empty, has a character other than an ASCII letter, digit or hyphen, or is a numeric pre-release
     *     identifier with a leading zero
     */
    public Version {
        checkArgument(major >= 0, "Not a semantic version, the major version is negative: %s", major);
        checkArgument(minor >= 0, "Not a semantic version, the minor version is negative: %s", minor);
        checkArgument(patch >= 0, "Not a semantic version, the patch version is negative: %s", patch);
        preRelease = copyOfIdentifiers(preRelease, true);
        build = copyOfIdentifiers(build, false);
    }

    /**
     * Returns a release version, without pre-release identifiers or build metadata.
     *
     * <pre>{@code
     * Version.of(1, 4, 0);  // 1.4.0
     * }</pre>
     *
     * @param major the major version
     * @param minor the minor version
     * @param patch the patch version
     * @return the version
     * @throws IllegalArgumentException if a version is negative
     */
    public static Version of(long major, long minor, long patch) {
        return new Version(major, minor, patch, List.of(), List.of());
    }

    /**
     * Parses a version, such as {@code 1.4.0-rc.1+build.5}. The text is the version alone: no {@code v} prefix, no
     * whitespace.
     *
     * @param text the text to parse
     * @return the version
     * @throws IllegalArgumentException if {@code text} is not a version as Semantic Versioning defines it, or if its
     *     major, minor or patch version is larger than {@link Long#MAX_VALUE}
     */
    public static Version parse(String text) {
        requireNonNull(text, "text");
        Parts parts = Parts.of(text);
        String problem = parts.problem();
        // Not checkArgument: it would quote the text on every call, not only on the failing ones.
        if (problem != null) {
            throw new IllegalArgumentException("Not a semantic version, " + problem + ": " + quoted(text));
        }
        return parts.version();
    }

    /**
     * Parses a version as {@link #parse(String)} does, unless the text is not one.
     *
     * <pre>{@code
     * Version.tryParse("1.4.0");   // Optional[1.4.0]
     * Version.tryParse("v1.4.0");  // Optional.empty
     * }</pre>
     *
     * @param text the text to parse
     * @return the version, or empty if {@code text} is not a version that {@link #parse(String)} accepts
     */
    public static Optional<Version> tryParse(String text) {
        requireNonNull(text, "text");
        Parts parts = Parts.of(text);
        return parts.problem() == null ? Optional.of(parts.version()) : Optional.empty();
    }

    /**
     * Returns whether this version is a pre-release: whether it has pre-release identifiers.
     *
     * @return {@code true} if this version is a pre-release
     */
    public boolean isPreRelease() {
        return !preRelease.isEmpty();
    }

    /**
     * Compares this version with another by precedence, as Semantic Versioning defines it, ignoring build metadata.
     *
     * @param other the version to compare with
     * @return a negative number, zero or a positive number if this version has a lower, the same or a higher precedence
     */
    @Override
    public int compareTo(Version other) {
        requireNonNull(other, "other");
        int result = Long.compare(major, other.major);
        if (result == 0) {
            result = Long.compare(minor, other.minor);
        }
        if (result == 0) {
            result = Long.compare(patch, other.patch);
        }
        if (result != 0) {
            return result;
        }
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            return Boolean.compare(preRelease.isEmpty(), other.preRelease.isEmpty());
        }
        for (int i = 0; i < Math.min(preRelease.size(), other.preRelease.size()); i++) {
            result = compareIdentifiers(preRelease.get(i), other.preRelease.get(i));
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(preRelease.size(), other.preRelease.size());
    }

    /**
     * Returns this version as Semantic Versioning writes it, such as {@code 1.4.0-rc.1+build.5}: the text that
     * {@link #parse(String)} reads it from.
     *
     * @return this version as text
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        text.append(major).append('.').append(minor).append('.').append(patch);
        if (!preRelease.isEmpty()) {
            text.append('-').append(String.join(".", preRelease));
        }
        if (!build.isEmpty()) {
            text.append('+').append(String.join(".", build));
        }
        return text.toString();
    }

    private static List<String> copyOfIdentifiers(List<String> identifiers, boolean preRelease) {
        String name = preRelease ? "preRelease" : "build";
        requireNonNull(identifiers, name);
        List<String> copy = new ArrayList<>(identifiers.size());
        for (String identifier : identifiers) {
            requireNonNull(identifier, name + " identifier");
            String problem = problemWithIdentifier(identifier, preRelease);
            checkArgument(problem == null, "Not a semantic version, %s", problem);
            copy.add(identifier);
        }
        return List.copyOf(copy);
    }

    private static @Nullable String problemWithNumber(String number, String name) {
        String problem = null;
        if (number.isEmpty()) {
            problem = "is empty";
        } else if (!isNumeric(number)) {
            problem = "is not a number";
        } else if (hasLeadingZero(number)) {
            problem = "has a leading zero";
        } else if (compareNumbers(number, MAX_NUMBER) > 0) {
            problem = "is larger than " + MAX_NUMBER;
        }
        return problem == null ? null : "the " + name + " version " + problem;
    }

    private static @Nullable String problemWithIdentifiers(@Nullable List<String> identifiers, boolean preRelease) {
        for (String identifier : identifiers == null ? List.<String>of() : identifiers) {
            String problem = problemWithIdentifier(identifier, preRelease);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    private static @Nullable String problemWithIdentifier(String identifier, boolean preRelease) {
        String kind = preRelease ? "pre-release" : "build";
        if (identifier.isEmpty()) {
            return "a " + kind + " identifier is empty";
        }
        if (!identifier.chars().allMatch(c -> isAsciiDigit(c) || isAsciiLetter(c) || c == '-')) {
            return "the " + kind + " identifier " + quoted(identifier)
                    + " has a character other than an ASCII letter, digit or hyphen";
        }
        if (preRelease && isNumeric(identifier) && hasLeadingZero(identifier)) {
            return "the numeric pre-release identifier " + quoted(identifier) + " has a leading zero";
        }
        return null;
    }

    // Numeric identifiers compare as numbers, whatever their size, and before alphanumeric identifiers.
    private static int compareIdentifiers(String left, String right) {
        boolean leftIsNumeric = isNumeric(left);
        boolean rightIsNumeric = isNumeric(right);
        if (leftIsNumeric && rightIsNumeric) {
            return compareNumbers(left, right);
        }
        if (leftIsNumeric || rightIsNumeric) {
            return leftIsNumeric ? -1 : 1;
        }
        return left.compareTo(right);
    }

    // Compares numbers without leading zeros, whatever their size.
    private static int compareNumbers(String left, String right) {
        int result = Integer.compare(left.length(), right.length());
        return result != 0 ? result : left.compareTo(right);
    }

    private static boolean isNumeric(String text) {
        return text.chars().allMatch(Version::isAsciiDigit);
    }

    private static boolean hasLeadingZero(String number) {
        return number.length() > 1 && number.charAt(0) == '0';
    }

    private static boolean isAsciiDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAsciiLetter(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    // A control character would end up raw in the message, and in whatever shows it.
    private static String quoted(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        text.chars()
                .forEach(c -> quoted.append(
                        Character.isISOControl(c)
                                ? String.format(Locale.ROOT, "\\u%04x", c)
                                : String.valueOf((char) c)));
        return quoted.append('"').toString();
    }

    /**
     * The parts of a text that may be a version: the dot-separated parts before the first {@code -} or {@code +}, then
     * the dot-separated parts after that {@code -}, if any, then those after the first {@code +}, if any. Identifiers
     * may contain {@code -}, but not {@code +}.
     */
    private record Parts(
            List<String> numbers,
            @Nullable List<String> preRelease,
            @Nullable List<String> build) {

        private static final List<String> NAMES = List.of("major", "minor", "patch");

        static Parts of(String text) {
            int plus = text.indexOf('+');
            String beforeBuild = plus < 0 ? text : text.substring(0, plus);
            int dash = beforeBuild.indexOf('-');
            return new Parts(
                    DOT.split(dash < 0 ? beforeBuild : beforeBuild.substring(0, dash)),
                    dash < 0 ? null : DOT.split(beforeBuild.substring(dash + 1)),
                    plus < 0 ? null : DOT.split(text.substring(plus + 1)));
        }

        // What makes these parts not a version, if anything.
        @Nullable
        String problem() {
            if (numbers.size() != NAMES.size()) {
                return "it needs a major, a minor and a patch version, separated by dots";
            }
            String problem = null;
            for (int i = 0; problem == null && i < NAMES.size(); i++) {
                problem = problemWithNumber(numbers.get(i), NAMES.get(i));
            }
            if (problem == null) {
                problem = problemWithIdentifiers(preRelease, true);
            }
            if (problem == null) {
                problem = problemWithIdentifiers(build, false);
            }
            return problem;
        }

        // The version, if there is no problem.
        Version version() {
            return new Version(
                    Long.parseLong(numbers.get(0)),
                    Long.parseLong(numbers.get(1)),
                    Long.parseLong(numbers.get(2)),
                    preRelease == null ? List.of() : preRelease,
                    build == null ? List.of() : build);
        }
    }
}
