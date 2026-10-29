package be.imgn.gaia.core.text;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * A naming convention: a way to write a name made of several words, such as {@code timeoutSeconds} or
 * {@code timeout-seconds}, and the conversion from one to another.
 *
 * <pre>{@code
 * NamingConvention.LOWER_CAMEL.toLowerHyphen("timeoutSeconds")       // timeout-seconds
 * NamingConvention.UPPER_UNDERSCORE.toLowerHyphen("TIMEOUT_SECONDS")  // timeout-seconds
 * NamingConvention.LOWER_HYPHEN.toUpperCamel("http-url")              // HttpUrl
 * NamingConvention.LOWER_HYPHEN.from("TIMEOUT_SECONDS")               // timeout-seconds
 * }</pre>
 *
 * <p>{@link #to(NamingConvention, String)} converts to a convention chosen at run time.
 *
 * <p>A conversion splits the text into words, then joins the words in the target convention. Letters change case one
 * code point at a time, with {@link Character#toLowerCase(int)}, {@link Character#toUpperCase(int)} and
 * {@link Character#toTitleCase(int)}: the result never depends on the default locale.
 *
 * <p>In camel case, a word starts at an uppercase letter that follows a lowercase letter, a letter without case or a
 * digit, and at the last uppercase letter of a run that a lowercase letter follows. Digits stay in the word they
 * follow. Converted to {@link #LOWER_HYPHEN}:
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
 * <p>The text to convert must follow its convention, or any convention for {@link #from(String)}, or the conversion
 * throws {@link IllegalArgumentException}. It must not be empty, must start with a letter, and may contain only
 * letters, digits and the delimiter of its convention, without empty words: no delimiter at the end, and no two
 * delimiters in a row. Its letters must fit the convention:
 *
 * <ul>
 *   <li>{@link #LOWER_CAMEL} text must not start with an uppercase or titlecase letter, and {@link #UPPER_CAMEL} text
 *       must start with one.
 *   <li>{@link #LOWER_HYPHEN}, {@link #LOWER_UNDERSCORE} and {@link #LOWER_DOT} text must not contain any uppercase or
 *       titlecase letter.
 *   <li>{@link #UPPER_UNDERSCORE} text must not contain any lowercase letter.
 * </ul>
 *
 * <p>A letter of the wrong case is accepted where the conversion to the convention would leave it unchanged, so that a
 * convention accepts every text converted to it: {@code ß}, which {@link Character#toUpperCase(int)} keeps, may appear
 * in {@link #UPPER_UNDERSCORE} text, and {@code 名}, which has no case, may start {@link #UPPER_CAMEL} text.
 *
 * @since 0.1.2
 */
public enum NamingConvention {

    /** Words joined together, each one capitalized except the first: {@code timeoutSeconds}. */
    LOWER_CAMEL(false),

    /** Words joined together, each one capitalized: {@code TimeoutSeconds}. */
    UPPER_CAMEL(true),

    /** Lowercase words separated by hyphens: {@code timeout-seconds}. */
    LOWER_HYPHEN('-', false),

    /** Lowercase words separated by underscores: {@code timeout_seconds}. */
    LOWER_UNDERSCORE('_', false),

    /** Uppercase words separated by underscores: {@code TIMEOUT_SECONDS}. */
    UPPER_UNDERSCORE('_', true),

    /** Lowercase words separated by dots: {@code timeout.seconds}. */
    LOWER_DOT('.', false);

    private final boolean camel;
    private final char delimiter;
    private final boolean upper;

    NamingConvention(boolean upper) {
        this(true, '\0', upper);
    }

    NamingConvention(char delimiter, boolean upper) {
        this(false, delimiter, upper);
    }

    NamingConvention(boolean camel, char delimiter, boolean upper) {
        this.camel = camel;
        this.delimiter = delimiter;
        this.upper = upper;
    }

    /**
     * Converts text that follows this convention to another convention.
     *
     * @param target the convention to convert to
     * @param text the text, following this convention
     * @return {@code text} following {@code target}
     * @throws IllegalArgumentException if {@code text} does not follow this convention
     */
    public String to(NamingConvention target, String text) {
        requireNonNull(target, "target");
        requireNonNull(text, "text");
        return target.join(split(text));
    }

    /**
     * Converts text that follows this convention to {@link #LOWER_CAMEL}.
     *
     * @param text the text, following this convention
     * @return {@code text} following {@link #LOWER_CAMEL}
     * @throws IllegalArgumentException if {@code text} does not follow this convention
     */
    public String toLowerCamel(String text) {
        return to(LOWER_CAMEL, text);
    }

    /**
     * Converts text that follows this convention to {@link #UPPER_CAMEL}.
     *
     * @param text the text, following this convention
     * @return {@code text} following {@link #UPPER_CAMEL}
     * @throws IllegalArgumentException if {@code text} does not follow this convention
     */
    public String toUpperCamel(String text) {
        return to(UPPER_CAMEL, text);
    }

    /**
     * Converts text that follows this convention to {@link #LOWER_HYPHEN}.
     *
     * @param text the text, following this convention
     * @return {@code text} following {@link #LOWER_HYPHEN}
     * @throws IllegalArgumentException if {@code text} does not follow this convention
     */
    public String toLowerHyphen(String text) {
        return to(LOWER_HYPHEN, text);
    }

    /**
     * Converts text that follows this convention to {@link #LOWER_UNDERSCORE}.
     *
     * @param text the text, following this convention
     * @return {@code text} following {@link #LOWER_UNDERSCORE}
     * @throws IllegalArgumentException if {@code text} does not follow this convention
     */
    public String toLowerUnderscore(String text) {
        return to(LOWER_UNDERSCORE, text);
    }

    /**
     * Converts text that follows this convention to {@link #UPPER_UNDERSCORE}.
     *
     * @param text the text, following this convention
     * @return {@code text} following {@link #UPPER_UNDERSCORE}
     * @throws IllegalArgumentException if {@code text} does not follow this convention
     */
    public String toUpperUnderscore(String text) {
        return to(UPPER_UNDERSCORE, text);
    }

    /**
     * Converts text that follows this convention to {@link #LOWER_DOT}.
     *
     * @param text the text, following this convention
     * @return {@code text} following {@link #LOWER_DOT}
     * @throws IllegalArgumentException if {@code text} does not follow this convention
     */
    public String toLowerDot(String text) {
        return to(LOWER_DOT, text);
    }

    /**
     * Converts text that follows any convention to this one. Text that several conventions accept, such as {@code name}
     * or {@code NAME}, is a single word, so the result does not depend on the convention it is read in.
     *
     * @param text the text, following any convention
     * @return {@code text} following this convention
     * @throws IllegalArgumentException if {@code text} follows no convention, such as {@code timeout-Seconds} or
     *     {@code timeout-seconds_max}
     */
    public String from(String text) {
        requireNonNull(text, "text");
        var source = Arrays.stream(values()).filter(c -> c.isWrittenIn(text)).findFirst();
        checkArgument(source.isPresent(), "\"%s\" follows no naming convention", text);
        return join(source.orElseThrow().split(text));
    }

    private List<String> split(String text) {
        checkArgument(isWrittenIn(text), "\"%s\" does not follow %s", text, this);
        return camel ? splitCamel(text) : Splitter.on(delimiter).split(text);
    }

    private boolean isWrittenIn(String text) {
        return !text.isEmpty()
                && Character.isLetter(text.codePointAt(0))
                && (camel ? fitsFirstOfCamel(text.codePointAt(0)) : hasNoEmptyWord(text))
                && text.codePoints().allMatch(this::isAllowed);
    }

    private boolean hasNoEmptyWord(String text) {
        return text.charAt(text.length() - 1) != delimiter
                && !text.contains(String.valueOf(new char[] {delimiter, delimiter}));
    }

    // A letter of the wrong case fits when the conversion leaves it unchanged, since the conversion produces it.
    private boolean fitsFirstOfCamel(int codePoint) {
        return upper
                ? isUpper(codePoint) || Character.toTitleCase(codePoint) == codePoint
                : !isUpper(codePoint) || Character.toLowerCase(codePoint) == codePoint;
    }

    private boolean isAllowed(int codePoint) {
        if (Character.isLetter(codePoint)) {
            return camel
                    || (upper
                            ? !Character.isLowerCase(codePoint) || Character.toUpperCase(codePoint) == codePoint
                            : !isUpper(codePoint) || Character.toLowerCase(codePoint) == codePoint);
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
