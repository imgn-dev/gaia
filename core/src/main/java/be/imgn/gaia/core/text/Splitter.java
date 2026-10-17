package be.imgn.gaia.core.text;

import static java.util.Objects.requireNonNull;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

/**
 * Splits text into parts around a separator: a character, a literal string or a regular expression.
 *
 * <pre>{@code
 * Splitter.on(',').trimResults().omitEmptyStrings().split(" a, ,b ,, c ")  // [a, b, c]
 * Splitter.on("::").split("a::b")                                            // [a, b]
 * Splitter.onPattern("\\s+").split("a  b\tc")                                // [a, b, c]
 * }</pre>
 *
 * <p>Unlike {@link String#split(String)}:
 *
 * <ul>
 *   <li>A string separator is literal, never a regular expression: {@code on(".")} splits on dots.
 *   <li>Nothing is dropped unless asked: empty parts are kept, trailing ones included, so {@code on(':')} splits
 *       {@code "a::b:"} into {@code [a, , b, ]}. Splitting the empty string gives one empty part.
 * </ul>
 *
 * <p>A splitter is immutable, thread-safe and reusable: {@link #trimResults()}, {@link #omitEmptyStrings()} and
 * {@link #limit(int)} return a new splitter and leave this one unchanged.
 */
public final class Splitter {

    private static final int NO_LIMIT = Integer.MAX_VALUE;

    private final Strategy strategy;
    private final boolean trimResults;
    private final boolean omitEmptyStrings;
    private final int limit;

    private Splitter(Strategy strategy, boolean trimResults, boolean omitEmptyStrings, int limit) {
        this.strategy = strategy;
        this.trimResults = trimResults;
        this.omitEmptyStrings = omitEmptyStrings;
        this.limit = limit;
    }

    /**
     * Returns a splitter on a character.
     *
     * @param separator the separator
     * @return a splitter on {@code separator}
     */
    public static Splitter on(char separator) {
        return new Splitter((text, from) -> separatorAt(text.indexOf(separator, from), 1), false, false, NO_LIMIT);
    }

    /**
     * Returns a splitter on a literal string. The string is not a regular expression.
     *
     * @param separator the separator
     * @return a splitter on {@code separator}
     * @throws IllegalArgumentException if {@code separator} is empty
     */
    public static Splitter on(String separator) {
        requireNonNull(separator, "separator");
        if (separator.isEmpty()) {
            throw new IllegalArgumentException("The separator must not be empty");
        }
        return new Splitter(
                (text, from) -> separatorAt(text.indexOf(separator, from), separator.length()), false, false, NO_LIMIT);
    }

    /**
     * Returns a splitter on the matches of a regular expression. Matches of length zero are ignored.
     *
     * @param separator the regular expression
     * @return a splitter on {@code separator}
     * @throws IllegalArgumentException if {@code separator} matches the empty string
     */
    public static Splitter on(Pattern separator) {
        requireNonNull(separator, "separator");
        if (separator.matcher("").matches()) {
            throw new IllegalArgumentException("The separator must not match the empty string: " + separator);
        }
        return new Splitter((text, from) -> nextMatch(separator.matcher(text), from), false, false, NO_LIMIT);
    }

    /**
     * Returns a splitter on the matches of a regular expression, given as a string. Matches of length zero are ignored.
     *
     * @param separator the regular expression, in the syntax of {@link Pattern}
     * @return a splitter on {@code separator}
     * @throws java.util.regex.PatternSyntaxException if {@code separator} is not a valid regular expression
     * @throws IllegalArgumentException if {@code separator} matches the empty string
     */
    public static Splitter onPattern(String separator) {
        requireNonNull(separator, "separator");
        return on(Pattern.compile(separator));
    }

    private static @Nullable Separator separatorAt(int index, int length) {
        return index < 0 ? null : new Separator(index, index + length);
    }

    private static @Nullable Separator nextMatch(Matcher matcher, int from) {
        int searchFrom = from;
        while (searchFrom <= matcher.regionEnd() && matcher.find(searchFrom)) {
            if (matcher.end() > matcher.start()) {
                return new Separator(matcher.start(), matcher.end());
            }
            searchFrom = matcher.start() + 1;
        }
        return null;
    }

    /**
     * Returns a splitter like this one that removes leading and trailing whitespace from each part, as
     * {@link String#strip()} does.
     *
     * @return a trimming splitter
     */
    public Splitter trimResults() {
        return new Splitter(strategy, true, omitEmptyStrings, limit);
    }

    /**
     * Returns a splitter like this one that leaves out empty parts. With {@link #trimResults()}, a part is empty when
     * nothing is left after trimming.
     *
     * @return a splitter that omits empty parts
     */
    public Splitter omitEmptyStrings() {
        return new Splitter(strategy, trimResults, true, limit);
    }

    /**
     * Returns a splitter like this one that stops after {@code limit} parts: the last part holds the rest of the text,
     * separators included. Parts left out by {@link #omitEmptyStrings()} do not count.
     *
     * <p>For example, {@code Splitter.on(',').omitEmptyStrings().limit(3)} splits {@code "a,,,b,,,c,d"} into {@code [a,
     * b, c,d]}.
     *
     * @param limit the maximum number of parts
     * @return a splitter with the given limit
     * @throws IllegalArgumentException if {@code limit} is not positive
     */
    public Splitter limit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("The limit must be positive: " + limit);
        }
        return new Splitter(strategy, trimResults, omitEmptyStrings, limit);
    }

    /**
     * Splits {@code text}.
     *
     * @param text the text to split
     * @return the parts, in order, as an unmodifiable list
     */
    public List<String> split(CharSequence text) {
        requireNonNull(text, "text");
        return splitToStream(text).toList();
    }

    /**
     * Splits {@code text} lazily: each part is found when the stream reaches it.
     *
     * @param text the text to split
     * @return the parts, in order
     */
    public Stream<String> splitToStream(CharSequence text) {
        requireNonNull(text, "text");
        Iterator<String> parts = new Parts(text.toString());
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(parts, Spliterator.ORDERED | Spliterator.NONNULL), false);
    }

    /** Finds the next separator in a text. */
    @FunctionalInterface
    private interface Strategy {

        @Nullable
        Separator find(String text, int from);
    }

    /** The position of a separator in a text: from {@code start}, inclusive, to {@code end}, exclusive. */
    private record Separator(int start, int end) {}

    /** The parts of one text, found one at a time. */
    private final class Parts implements Iterator<String> {

        private final String text;
        private int position;
        private int returned;
        private boolean exhausted;
        private @Nullable String next;

        Parts(String text) {
            this.text = text;
        }

        @Override
        public boolean hasNext() {
            if (next == null && !exhausted) {
                next = findNext();
            }
            return next != null;
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String part = requireNonNull(next);
            next = null;
            return part;
        }

        private @Nullable String findNext() {
            while (!exhausted) {
                int start = position;
                Separator separator = strategy.find(text, position);
                int end;
                if (separator == null) {
                    end = text.length();
                    exhausted = true;
                } else {
                    end = separator.start();
                    position = separator.end();
                }
                String part = clean(text.substring(start, end));
                if (omitEmptyStrings && part.isEmpty()) {
                    continue;
                }
                returned++;
                if (returned == limit && !exhausted) {
                    exhausted = true;
                    return clean(text.substring(start));
                }
                return part;
            }
            return null;
        }

        private String clean(String part) {
            return trimResults ? part.strip() : part;
        }
    }
}
