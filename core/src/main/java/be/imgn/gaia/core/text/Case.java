package be.imgn.gaia.core.text;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * A way to write a name made of several words, such as {@code timeoutSeconds} or {@code timeout-seconds}, and the
 * conversion from one to another.
 *
 * <pre>{@code
 * Case.LOWER_CAMEL.to(Case.KEBAB, "timeoutSeconds")   // timeout-seconds
 * Case.UPPER_SNAKE.to(Case.KEBAB, "TIMEOUT_SECONDS")  // timeout-seconds
 * Case.KEBAB.to(Case.UPPER_CAMEL, "http-url")         // HttpUrl
 * }</pre>
 *
 * <p>A conversion splits the text into words, then joins the words in the target case. Letters change case one code
 * point at a time, with {@link Character#toLowerCase(int)}, {@link Character#toUpperCase(int)} and
 * {@link Character#toTitleCase(int)}: the result never depends on the default locale.
 *
 * <p>In camel case, a word starts at an uppercase letter that follows a lowercase letter, a letter without case or a
 * digit, and at the last uppercase letter of a run that a lowercase letter follows. Digits stay in the word they
 * follow. Converted to {@link #KEBAB}:
 *
 * <ul>
 *   <li>{@code timeoutSeconds} gives {@code timeout-seconds}.
 *   <li>{@code httpURL} gives {@code http-url}, and {@code httpURLPath} gives {@code http-url-path}.
 *   <li>{@code ipv4Address} gives {@code ipv4-address}, {@code utf8} gives {@code utf8}, and {@code HTTP2Server} gives
 *       {@code http2-server}.
 *   <li>{@code IPv4Address} gives {@code i-pv4-address}: the {@code P} starts a word, since a lowercase letter follows
 *       it.
 * </ul>
 *
 * <p>Joined in camel case, a word keeps only its first letter in uppercase, so an acronym does not survive a round
 * trip: {@code http-url} gives {@code httpUrl}, not {@code httpURL}.
 *
 * <p>The text to convert must be written in its case, or the conversion throws {@link IllegalArgumentException}. It
 * must not be empty, must start with a letter, and may contain only letters, digits and the delimiter of its case,
 * without empty words: no delimiter at the end, and no two delimiters in a row. Its letters must fit the case:
 *
 * <ul>
 *   <li>{@link #LOWER_CAMEL} text must not start with an uppercase or titlecase letter, and {@link #UPPER_CAMEL} text
 *       must start with one.
 *   <li>{@link #KEBAB}, {@link #SNAKE} and {@link #DOT} text must not contain any uppercase or titlecase letter.
 *   <li>{@link #UPPER_SNAKE} text must not contain any lowercase letter.
 * </ul>
 *
 * @since 0.1.2
 */
public enum Case {

    /** Words joined together, each one capitalized except the first: {@code timeoutSeconds}. */
    LOWER_CAMEL(false),

    /** Words joined together, each one capitalized: {@code TimeoutSeconds}. */
    UPPER_CAMEL(true),

    /** Lowercase words separated by hyphens: {@code timeout-seconds}. */
    KEBAB('-', false),

    /** Lowercase words separated by underscores: {@code timeout_seconds}. */
    SNAKE('_', false),

    /** Uppercase words separated by underscores: {@code TIMEOUT_SECONDS}. */
    UPPER_SNAKE('_', true),

    /** Lowercase words separated by dots: {@code timeout.seconds}. */
    DOT('.', false);

    private final boolean camel;
    private final char delimiter;
    private final boolean upper;

    Case(boolean upper) {
        this(true, '\0', upper);
    }

    Case(char delimiter, boolean upper) {
        this(false, delimiter, upper);
    }

    Case(boolean camel, char delimiter, boolean upper) {
        this.camel = camel;
        this.delimiter = delimiter;
        this.upper = upper;
    }

    /**
     * Converts text written in this case to another case.
     *
     * @param target the case to convert to
     * @param text the text, written in this case
     * @return {@code text} written in {@code target}
     * @throws IllegalArgumentException if {@code text} is not written in this case
     */
    public String to(Case target, String text) {
        requireNonNull(target, "target");
        requireNonNull(text, "text");
        return target.join(split(text));
    }

    private List<String> split(String text) {
        checkArgument(isWrittenIn(text), "\"%s\" is not %s text", text, this);
        return camel ? splitCamel(text) : Splitter.on(delimiter).split(text);
    }

    private boolean isWrittenIn(String text) {
        return !text.isEmpty()
                && Character.isLetter(text.codePointAt(0))
                && (camel ? isUpper(text.codePointAt(0)) == upper : hasNoEmptyWord(text))
                && text.codePoints().allMatch(this::isAllowed);
    }

    private boolean hasNoEmptyWord(String text) {
        return text.charAt(text.length() - 1) != delimiter
                && !text.contains(String.valueOf(new char[] {delimiter, delimiter}));
    }

    private boolean isAllowed(int codePoint) {
        if (Character.isLetter(codePoint)) {
            return camel || (upper ? !Character.isLowerCase(codePoint) : !isUpper(codePoint));
        }
        return Character.isDigit(codePoint) || (!camel && codePoint == delimiter);
    }

    private static List<String> splitCamel(String text) {
        var codePoints = text.codePoints().toArray();
        var words = new ArrayList<String>();
        var start = 0;
        for (var i = 1; i < codePoints.length; i++) {
            if (startsWord(codePoints, i)) {
                words.add(new String(codePoints, start, i - start));
                start = i;
            }
        }
        words.add(new String(codePoints, start, codePoints.length - start));
        return words;
    }

    private static boolean startsWord(int[] codePoints, int index) {
        return isUpper(codePoints[index])
                && (!isUpper(codePoints[index - 1])
                        || (index + 1 < codePoints.length && Character.isLowerCase(codePoints[index + 1])));
    }

    private String join(List<String> words) {
        var joined = new StringBuilder();
        for (var word : words) {
            if (camel) {
                appendCamelWord(joined, word);
            } else {
                if (!joined.isEmpty()) {
                    joined.append(delimiter);
                }
                append(joined, word, upper ? Character::toUpperCase : Character::toLowerCase);
            }
        }
        return joined.toString();
    }

    private void appendCamelWord(StringBuilder joined, String word) {
        var first = word.codePointAt(0);
        var capitalized = upper || !joined.isEmpty();
        joined.appendCodePoint(capitalized ? Character.toTitleCase(first) : Character.toLowerCase(first));
        append(joined, word.substring(Character.charCount(first)), Character::toLowerCase);
    }

    private static void append(StringBuilder joined, String word, IntUnaryOperator conversion) {
        word.codePoints().map(conversion).forEach(joined::appendCodePoint);
    }

    private static boolean isUpper(int codePoint) {
        return Character.isUpperCase(codePoint) || Character.isTitleCase(codePoint);
    }
}
