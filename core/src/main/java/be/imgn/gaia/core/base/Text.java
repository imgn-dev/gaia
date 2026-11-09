package be.imgn.gaia.core.base;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import org.jspecify.annotations.Nullable;

/**
 * Finds text in a string, to extract, remove or replace it.
 *
 * <pre>{@code
 * Text.prefix("www.").removeFrom("www.example.com")                   // example.com
 * Text.suffix('/').addToIfAbsent("/api")                              // /api/
 * Text.last('.').removeFrom("archive.tar.gz")                         // archive.targz
 * Text.first('@').followedBy("gmail.com").replaceFrom(s, " at ")      // the first @ before gmail.com
 * Text.first(Pattern.compile("\\d+")).extractFrom("v12.3")            // Optional[12]
 * }</pre>
 *
 * <p>A {@link Pattern} selects at most one occurrence in a text: the first one, the last one, or the one at the start
 * or the end. Conditions narrow it down to occurrences that are, or are not, preceded or followed by a literal string.
 * A literal string or character is never a regular expression, and an empty one is rejected.
 *
 * @since 0.2.0
 */
public final class Text {

    private Text() {}

    /**
     * Returns a pattern that selects the first occurrence of a literal string.
     *
     * @param literal the string to find
     * @return a pattern on the first occurrence of {@code literal}
     * @throws IllegalArgumentException if {@code literal} is empty
     */
    public static Pattern first(String literal) {
        return new LiteralSearch(Anchor.FIRST, checkLiteral(literal), List.of());
    }

    /**
     * Returns a pattern that selects the first occurrence of a character.
     *
     * @param literal the character to find
     * @return a pattern on the first occurrence of {@code literal}
     */
    public static Pattern first(char literal) {
        return first(String.valueOf(literal));
    }

    /**
     * Returns a pattern that selects the first match of a regular expression, as {@link Matcher#find()} finds it.
     * Matches of length zero are ignored.
     *
     * @param regex the regular expression
     * @return a pattern on the first match of {@code regex}
     * @throws IllegalArgumentException if {@code regex} matches the empty string
     */
    public static Pattern first(java.util.regex.Pattern regex) {
        return new RegexSearch(false, checkRegex(regex), List.of());
    }

    /**
     * Returns a pattern that selects the last occurrence of a literal string.
     *
     * @param literal the string to find
     * @return a pattern on the last occurrence of {@code literal}
     * @throws IllegalArgumentException if {@code literal} is empty
     */
    public static Pattern last(String literal) {
        return new LiteralSearch(Anchor.LAST, checkLiteral(literal), List.of());
    }

    /**
     * Returns a pattern that selects the last occurrence of a character.
     *
     * @param literal the character to find
     * @return a pattern on the last occurrence of {@code literal}
     */
    public static Pattern last(char literal) {
        return last(String.valueOf(literal));
    }

    /**
     * Returns a pattern that selects the last match of a regular expression: the last of the matches that
     * {@link Matcher#find()} finds in turn, from left to right. Matches of length zero are ignored.
     *
     * <p>For example, the last match of {@code \d+} in {@code "a12"} is {@code 12}, not {@code 2}.
     *
     * @param regex the regular expression
     * @return a pattern on the last match of {@code regex}
     * @throws IllegalArgumentException if {@code regex} matches the empty string
     */
    public static Pattern last(java.util.regex.Pattern regex) {
        return new RegexSearch(true, checkRegex(regex), List.of());
    }

    /**
     * Returns an affix that a text must start with.
     *
     * @param literal the prefix
     * @return the prefix {@code literal}
     * @throws IllegalArgumentException if {@code literal} is empty
     */
    public static Affix prefix(String literal) {
        return new Affix(Anchor.PREFIX, checkLiteral(literal));
    }

    /**
     * Returns an affix of one character that a text must start with.
     *
     * @param literal the prefix
     * @return the prefix {@code literal}
     */
    public static Affix prefix(char literal) {
        return prefix(String.valueOf(literal));
    }

    /**
     * Returns an affix that a text must end with.
     *
     * @param literal the suffix
     * @return the suffix {@code literal}
     * @throws IllegalArgumentException if {@code literal} is empty
     */
    public static Affix suffix(String literal) {
        return new Affix(Anchor.SUFFIX, checkLiteral(literal));
    }

    /**
     * Returns an affix of one character that a text must end with.
     *
     * @param literal the suffix
     * @return the suffix {@code literal}
     */
    public static Affix suffix(char literal) {
        return suffix(String.valueOf(literal));
    }

    private static String checkLiteral(String literal) {
        requireNonNull(literal, "literal");
        checkArgument(!literal.isEmpty(), "The literal must not be empty");
        return literal;
    }

    private static java.util.regex.Pattern checkRegex(java.util.regex.Pattern regex) {
        requireNonNull(regex, "regex");
        checkArgument(!regex.matcher("").matches(), "The regex must not match the empty string: %s", regex);
        return regex;
    }

    /**
     * Selects at most one occurrence in a text, to extract, remove or replace it.
     *
     * <p>Conditions select the first or last occurrence that satisfies all of them, not the first or last occurrence if
     * it does. They are checked against the whole text, right before or right after the occurrence, and are not part of
     * it: {@code first('@').followedBy("gmail.com")} selects only the {@code @}. Each condition returns a new pattern
     * and leaves this one unchanged.
     *
     * <p>A match of a regular expression that fails a condition is skipped, never shortened: the search resumes one
     * character after its start. So {@code first(Pattern.compile("\\d+")).followedBy("5")} finds nothing in
     * {@code "1235"}.
     *
     * <p>A pattern is immutable and thread-safe.
     *
     * @since 0.2.0
     */
    public sealed interface Pattern permits Affix, Search {

        /**
         * Returns a pattern like this one that selects only an occurrence immediately followed by {@code literal}.
         *
         * @param literal the text that must follow
         * @return a pattern with the condition
         * @throws IllegalArgumentException if {@code literal} is empty
         */
        Pattern followedBy(String literal);

        /**
         * Returns a pattern like this one that selects only an occurrence not immediately followed by {@code literal}.
         *
         * @param literal the text that must not follow
         * @return a pattern with the condition
         * @throws IllegalArgumentException if {@code literal} is empty
         */
        Pattern notFollowedBy(String literal);

        /**
         * Returns a pattern like this one that selects only an occurrence immediately preceded by {@code literal}.
         *
         * @param literal the text that must precede
         * @return a pattern with the condition
         * @throws IllegalArgumentException if {@code literal} is empty
         */
        Pattern precededBy(String literal);

        /**
         * Returns a pattern like this one that selects only an occurrence not immediately preceded by {@code literal}.
         *
         * @param literal the text that must not precede
         * @return a pattern with the condition
         * @throws IllegalArgumentException if {@code literal} is empty
         */
        Pattern notPrecededBy(String literal);

        /**
         * Returns the occurrence this pattern selects in {@code text}.
         *
         * @param text the text to search
         * @return the occurrence, or an empty optional if there is none
         */
        Optional<String> extractFrom(CharSequence text);

        /**
         * Removes the occurrence this pattern selects from {@code text}.
         *
         * @param text the text to search
         * @return {@code text} without the occurrence, or unchanged if there is none
         */
        String removeFrom(CharSequence text);

        /**
         * Replaces the occurrence this pattern selects in {@code text}. The replacement is literal: unlike with
         * {@link Matcher#replaceFirst(String)}, {@code $} and {@code \} have no special meaning.
         *
         * @param text the text to search
         * @param replacement the text to put in place of the occurrence
         * @return {@code text} with the occurrence replaced, or unchanged if there is none
         */
        String replaceFrom(CharSequence text, String replacement);
    }

    /**
     * A literal string that a text starts or ends with: a prefix or a suffix. As a {@link CharSequence}, an affix is
     * its literal, and {@link #toString()} returns it.
     *
     * <p>Two affixes are equal when they are both prefixes or both suffixes, with equal literals. An affix is never
     * equal to another kind of {@code CharSequence}, such as a {@code String}.
     *
     * <p>Conditions return a plain {@link Pattern}, not an affix.
     *
     * @since 0.2.0
     */
    public static final class Affix implements Pattern, CharSequence {

        private final LiteralSearch search;

        private Affix(Anchor anchor, String literal) {
            this.search = new LiteralSearch(anchor, literal, List.of());
        }

        @Override
        public Pattern followedBy(String literal) {
            return search.followedBy(literal);
        }

        @Override
        public Pattern notFollowedBy(String literal) {
            return search.notFollowedBy(literal);
        }

        @Override
        public Pattern precededBy(String literal) {
            return search.precededBy(literal);
        }

        @Override
        public Pattern notPrecededBy(String literal) {
            return search.notPrecededBy(literal);
        }

        @Override
        public Optional<String> extractFrom(CharSequence text) {
            return search.extractFrom(text);
        }

        @Override
        public String removeFrom(CharSequence text) {
            return search.removeFrom(text);
        }

        @Override
        public String replaceFrom(CharSequence text, String replacement) {
            return search.replaceFrom(text, replacement);
        }

        /**
         * Adds this affix to {@code text} unless it is already there: prepends a prefix, appends a suffix.
         *
         * @param text the text to add this affix to
         * @return {@code text} with this affix
         */
        public String addToIfAbsent(CharSequence text) {
            requireNonNull(text, "text");
            var string = text.toString();
            if (search.anchor == Anchor.PREFIX) {
                return string.startsWith(search.literal) ? string : search.literal + string;
            }
            return string.endsWith(search.literal) ? string : string + search.literal;
        }

        @Override
        public int length() {
            return search.literal.length();
        }

        @Override
        public char charAt(int index) {
            return search.literal.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return search.literal.substring(start, end);
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            return obj instanceof Affix other
                    && search.anchor == other.search.anchor
                    && search.literal.equals(other.search.literal);
        }

        @Override
        public int hashCode() {
            return 31 * search.literal.hashCode() + (search.anchor == Anchor.PREFIX ? 1 : 0);
        }

        /**
         * Returns the literal of this affix.
         *
         * @return the literal
         */
        @Override
        public String toString() {
            return search.literal;
        }
    }

    private enum Anchor {
        FIRST,
        LAST,
        PREFIX,
        SUFFIX
    }

    /** An occurrence in a text: from {@code start}, inclusive, to {@code end}, exclusive. */
    private record Occurrence(int start, int end) {}

    /** A literal that must, or must not, be right before or right after an occurrence. */
    private record Condition(boolean after, boolean negated, String literal) {

        boolean holds(String text, int start, int end) {
            var offset = after ? end : start - literal.length();
            return text.regionMatches(offset, literal, 0, literal.length()) != negated;
        }
    }

    /** A pattern that searches the occurrence itself. */
    private abstract static sealed class Search implements Pattern permits LiteralSearch, RegexSearch {

        private final List<Condition> conditions;

        Search(List<Condition> conditions) {
            this.conditions = conditions;
        }

        abstract @Nullable Occurrence find(String text);

        abstract Pattern withConditions(List<Condition> conditions);

        final boolean accepts(String text, int start, int end) {
            for (var condition : conditions) {
                if (!condition.holds(text, start, end)) {
                    return false;
                }
            }
            return true;
        }

        private Pattern with(boolean after, boolean negated, String literal) {
            var all = new ArrayList<>(conditions);
            all.add(new Condition(after, negated, checkLiteral(literal)));
            return withConditions(List.copyOf(all));
        }

        @Override
        public Pattern followedBy(String literal) {
            return with(true, false, literal);
        }

        @Override
        public Pattern notFollowedBy(String literal) {
            return with(true, true, literal);
        }

        @Override
        public Pattern precededBy(String literal) {
            return with(false, false, literal);
        }

        @Override
        public Pattern notPrecededBy(String literal) {
            return with(false, true, literal);
        }

        @Override
        public Optional<String> extractFrom(CharSequence text) {
            requireNonNull(text, "text");
            var string = text.toString();
            var occurrence = find(string);
            return occurrence == null
                    ? Optional.empty()
                    : Optional.of(string.substring(occurrence.start(), occurrence.end()));
        }

        @Override
        public String removeFrom(CharSequence text) {
            return replaceFrom(text, "");
        }

        @Override
        public String replaceFrom(CharSequence text, String replacement) {
            requireNonNull(text, "text");
            requireNonNull(replacement, "replacement");
            var string = text.toString();
            var occurrence = find(string);
            if (occurrence == null) {
                return string;
            }
            return string.substring(0, occurrence.start()) + replacement + string.substring(occurrence.end());
        }
    }

    private static final class LiteralSearch extends Search {

        private final Anchor anchor;
        private final String literal;

        LiteralSearch(Anchor anchor, String literal, List<Condition> conditions) {
            super(conditions);
            this.anchor = anchor;
            this.literal = literal;
        }

        @Override
        @Nullable
        Occurrence find(String text) {
            var start = switch (anchor) {
                case FIRST -> firstStart(text);
                case LAST -> lastStart(text);
                case PREFIX -> text.startsWith(literal) && accepts(text, 0) ? 0 : -1;
                case SUFFIX -> {
                    var i = text.length() - literal.length();
                    yield text.endsWith(literal) && accepts(text, i) ? i : -1;
                }
            };
            return start < 0 ? null : new Occurrence(start, start + literal.length());
        }

        private int firstStart(String text) {
            var i = text.indexOf(literal);
            while (i >= 0 && !accepts(text, i)) {
                i = text.indexOf(literal, i + 1);
            }
            return i;
        }

        private int lastStart(String text) {
            var i = text.lastIndexOf(literal);
            while (i >= 0 && !accepts(text, i)) {
                i = text.lastIndexOf(literal, i - 1);
            }
            return i;
        }

        private boolean accepts(String text, int start) {
            return accepts(text, start, start + literal.length());
        }

        @Override
        Pattern withConditions(List<Condition> conditions) {
            return new LiteralSearch(anchor, literal, conditions);
        }
    }

    private static final class RegexSearch extends Search {

        private final boolean last;
        private final java.util.regex.Pattern regex;

        RegexSearch(boolean last, java.util.regex.Pattern regex, List<Condition> conditions) {
            super(conditions);
            this.last = last;
            this.regex = regex;
        }

        // Scans the matches as Matcher.find() does in turn, except that after a match that fails a condition, the
        // scan resumes one character after its start rather than after its end.
        @Override
        @Nullable
        Occurrence find(String text) {
            Matcher matcher = regex.matcher(text);
            Occurrence found = null;
            var from = 0;
            while (from <= text.length() && matcher.find(from)) {
                var start = matcher.start();
                var end = matcher.end();
                if (end > start && accepts(text, start, end)) {
                    found = new Occurrence(start, end);
                    if (!last) {
                        return found;
                    }
                    from = end;
                } else {
                    from = start + 1;
                }
            }
            return found;
        }

        @Override
        Pattern withConditions(List<Condition> conditions) {
            return new RegexSearch(last, regex, conditions);
        }
    }
}
