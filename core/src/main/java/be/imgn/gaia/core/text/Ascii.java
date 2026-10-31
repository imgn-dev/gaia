package be.imgn.gaia.core.text;

import static java.util.Objects.requireNonNull;

/**
 * Classes, case mapping, comparison and trimming of ASCII characters, as many protocols, file formats and identifiers
 * define them.
 *
 * <pre>{@code
 * Ascii.equalsIgnoreCase("DocType", "DOCTYPE")  // true
 * Ascii.equalsIgnoreCase("PUBLıC", "PUBLIC")    // false
 * Ascii.toLowerCase("Text/HTML")                // text/html
 * Ascii.strip("\r\n utf-8\t")                   // utf-8
 * }</pre>
 *
 * <p>Only the 26 letters {@code A} to {@code Z} and {@code a} to {@code z} have a case here: every other character is
 * left as it is. Unlike {@link String#equalsIgnoreCase(String)} or {@link String#toLowerCase(java.util.Locale)}, these
 * methods never match or turn {@code ı} (U+0131), {@code ſ} (U+017F) or the Kelvin sign (U+212A) into {@code i},
 * {@code s} or {@code k}, and never depend on the default locale.
 *
 * <p>The methods that take a code point accept a {@code char}, which widens to it, and any {@code int}: one that is no
 * code point is in no class, and is returned unchanged. The methods that take text return a {@link String}: the text's
 * {@link CharSequence#toString()} itself if they change nothing, without copying it.
 *
 * @since 0.1.2
 */
public final class Ascii {

    private static final int CASE_DIFFERENCE = 'a' - 'A';

    private Ascii() {}

    /**
     * Returns whether a code point is an ASCII letter: {@code A} to {@code Z}, or {@code a} to {@code z}.
     *
     * @param c the code point
     * @return whether it is an ASCII letter
     */
    public static boolean isAlpha(int c) {
        return isUpperAlpha(c) || isLowerAlpha(c);
    }

    /**
     * Returns whether a code point is an ASCII uppercase letter: {@code A} to {@code Z}.
     *
     * @param c the code point
     * @return whether it is an ASCII uppercase letter
     */
    public static boolean isUpperAlpha(int c) {
        return c >= 'A' && c <= 'Z';
    }

    /**
     * Returns whether a code point is an ASCII lowercase letter: {@code a} to {@code z}.
     *
     * @param c the code point
     * @return whether it is an ASCII lowercase letter
     */
    public static boolean isLowerAlpha(int c) {
        return c >= 'a' && c <= 'z';
    }

    /**
     * Returns whether a code point is an ASCII digit: {@code 0} to {@code 9}.
     *
     * @param c the code point
     * @return whether it is an ASCII digit
     */
    public static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Returns whether a code point is an ASCII letter or digit.
     *
     * @param c the code point
     * @return whether it is an ASCII letter or digit
     */
    public static boolean isAlphanumeric(int c) {
        return isAlpha(c) || isDigit(c);
    }

    /**
     * Returns whether a code point is an ASCII hexadecimal digit: {@code 0} to {@code 9}, {@code A} to {@code F}, or
     * {@code a} to {@code f}.
     *
     * @param c the code point
     * @return whether it is an ASCII hexadecimal digit
     */
    public static boolean isHexDigit(int c) {
        return isUpperHexDigit(c) || (c >= 'a' && c <= 'f');
    }

    /**
     * Returns whether a code point is an ASCII uppercase hexadecimal digit: {@code 0} to {@code 9}, or {@code A} to
     * {@code F}.
     *
     * @param c the code point
     * @return whether it is an ASCII uppercase hexadecimal digit
     */
    public static boolean isUpperHexDigit(int c) {
        return isDigit(c) || (c >= 'A' && c <= 'F');
    }

    /**
     * Returns whether a code point is an ASCII lowercase hexadecimal digit: {@code 0} to {@code 9}, or {@code a} to
     * {@code f}.
     *
     * @param c the code point
     * @return whether it is an ASCII lowercase hexadecimal digit
     */
    public static boolean isLowerHexDigit(int c) {
        return isDigit(c) || (c >= 'a' && c <= 'f');
    }

    /**
     * Returns whether a code point is ASCII whitespace: tab (U+0009), line feed (U+000A), form feed (U+000C), carriage
     * return (U+000D) or space (U+0020). Line tabulation (U+000B, vertical tab) is not.
     *
     * @param c the code point
     * @return whether it is ASCII whitespace
     */
    public static boolean isWhitespace(int c) {
        return c == '\t' || c == '\n' || c == '\f' || c == '\r' || c == ' ';
    }

    /**
     * Returns the lowercase of a character if it is an ASCII uppercase letter, or the character itself.
     *
     * @param c the character
     * @return {@code a} to {@code z} for {@code A} to {@code Z}, or {@code c}
     */
    public static char toLowerCase(char c) {
        return isUpperAlpha(c) ? (char) (c + CASE_DIFFERENCE) : c;
    }

    /**
     * Returns the lowercase of a code point if it is an ASCII uppercase letter, or the code point itself.
     *
     * @param c the code point
     * @return {@code a} to {@code z} for {@code A} to {@code Z}, or {@code c}
     */
    public static int toLowerCase(int c) {
        return isUpperAlpha(c) ? c + CASE_DIFFERENCE : c;
    }

    /**
     * Returns text with its ASCII uppercase letters in lowercase.
     *
     * @param text the text
     * @return the text with {@code a} to {@code z} for {@code A} to {@code Z}
     */
    public static String toLowerCase(CharSequence text) {
        requireNonNull(text, "text");
        int length = text.length();
        int first = 0;
        while (first < length && !isUpperAlpha(text.charAt(first))) {
            first++;
        }
        if (first == length) {
            return text.toString();
        }
        var chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = toLowerCase(text.charAt(i));
        }
        return new String(chars);
    }

    /**
     * Returns the uppercase of a character if it is an ASCII lowercase letter, or the character itself.
     *
     * @param c the character
     * @return {@code A} to {@code Z} for {@code a} to {@code z}, or {@code c}
     */
    public static char toUpperCase(char c) {
        return isLowerAlpha(c) ? (char) (c - CASE_DIFFERENCE) : c;
    }

    /**
     * Returns the uppercase of a code point if it is an ASCII lowercase letter, or the code point itself.
     *
     * @param c the code point
     * @return {@code A} to {@code Z} for {@code a} to {@code z}, or {@code c}
     */
    public static int toUpperCase(int c) {
        return isLowerAlpha(c) ? c - CASE_DIFFERENCE : c;
    }

    /**
     * Returns text with its ASCII lowercase letters in uppercase.
     *
     * @param text the text
     * @return the text with {@code A} to {@code Z} for {@code a} to {@code z}
     */
    public static String toUpperCase(CharSequence text) {
        requireNonNull(text, "text");
        int length = text.length();
        int first = 0;
        while (first < length && !isLowerAlpha(text.charAt(first))) {
            first++;
        }
        if (first == length) {
            return text.toString();
        }
        var chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = toUpperCase(text.charAt(i));
        }
        return new String(chars);
    }

    /**
     * Returns whether two texts are equal when ASCII uppercase letters are taken as lowercase: {@code DocType} equals
     * {@code DOCTYPE}, but {@code PUBLıC} does not equal {@code PUBLIC}.
     *
     * @param a a text
     * @param b another text
     * @return whether they have the same length, and the same characters ignoring ASCII case
     */
    public static boolean equalsIgnoreCase(CharSequence a, CharSequence b) {
        requireNonNull(a, "a");
        requireNonNull(b, "b");
        return a.length() == b.length() && matches(a, 0, b);
    }

    /**
     * Returns whether a text starts with a prefix, ignoring ASCII case.
     *
     * @param text the text
     * @param prefix the prefix
     * @return whether the text starts with the prefix ignoring ASCII case; {@code true} for an empty prefix
     */
    public static boolean startsWithIgnoreCase(CharSequence text, CharSequence prefix) {
        requireNonNull(text, "text");
        requireNonNull(prefix, "prefix");
        return prefix.length() <= text.length() && matches(text, 0, prefix);
    }

    /**
     * Returns whether a region of a text, starting at an offset and as long as another text, equals that other text,
     * ignoring ASCII case.
     *
     * @param text the text
     * @param offset the index in {@code text} where the region starts
     * @param other the text to compare the region with
     * @return whether the region equals {@code other} ignoring ASCII case; {@code false} if it would run past the end
     *     of {@code text}, and {@code true} for an empty {@code other} at an offset up to the length of {@code text}
     * @throws IndexOutOfBoundsException if {@code offset} is negative
     */
    public static boolean regionMatchesIgnoreCase(CharSequence text, int offset, CharSequence other) {
        requireNonNull(text, "text");
        requireNonNull(other, "other");
        if (offset < 0) {
            throw new IndexOutOfBoundsException("offset is negative: " + offset);
        }
        return offset <= text.length() - other.length() && matches(text, offset, other);
    }

    // Whether text has other at offset, which leaves room for it.
    private static boolean matches(CharSequence text, int offset, CharSequence other) {
        for (int i = 0; i < other.length(); i++) {
            if (toLowerCase(text.charAt(offset + i)) != toLowerCase(other.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns text without the {@linkplain #isWhitespace(int) ASCII whitespace} at its start and its end.
     *
     * @param text the text
     * @return the text without leading and trailing ASCII whitespace
     */
    public static String strip(CharSequence text) {
        requireNonNull(text, "text");
        int end = contentEnd(text);
        return substring(text, contentStart(text, end), end);
    }

    /**
     * Returns text without the {@linkplain #isWhitespace(int) ASCII whitespace} at its start.
     *
     * @param text the text
     * @return the text without leading ASCII whitespace
     */
    public static String stripLeading(CharSequence text) {
        requireNonNull(text, "text");
        return substring(text, contentStart(text, text.length()), text.length());
    }

    /**
     * Returns text without the {@linkplain #isWhitespace(int) ASCII whitespace} at its end.
     *
     * @param text the text
     * @return the text without trailing ASCII whitespace
     */
    public static String stripTrailing(CharSequence text) {
        requireNonNull(text, "text");
        return substring(text, 0, contentEnd(text));
    }

    // The index of the first character before end that is not whitespace, or end if there is none.
    private static int contentStart(CharSequence text, int end) {
        int start = 0;
        while (start < end && isWhitespace(text.charAt(start))) {
            start++;
        }
        return start;
    }

    // The index after the last character that is not whitespace, or 0 if there is none.
    private static int contentEnd(CharSequence text) {
        int end = text.length();
        while (end > 0 && isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end;
    }

    private static String substring(CharSequence text, int start, int end) {
        return start == 0 && end == text.length()
                ? text.toString()
                : text.subSequence(start, end).toString();
    }
}
