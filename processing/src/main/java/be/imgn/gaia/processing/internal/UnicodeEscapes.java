package be.imgn.gaia.processing.internal;

import java.util.HexFormat;

/**
 * Writes Java source text in ASCII only, with Unicode escapes, so that it reads the same whatever the encoding javac
 * decodes it with.
 *
 * <p>Javac translates the Unicode escapes of a source before anything else (JLS 3.3), in identifiers, comments and
 * literals alike, so escaping a character is always the same as writing it, as long as the backslash that starts the
 * escape is eligible: preceded by an even number of contiguous backslashes. A character after an odd number of
 * backslashes, as in {@code // \é}, would make its escape one backslash too many; the last of those backslashes is
 * escaped too then, as <code>&#92;u005c</code>, which translates to the backslash without starting an escape itself.
 */
public final class UnicodeEscapes {

    private static final int LAST_ASCII = 0x7f;
    private static final String ESCAPED_BACKSLASH = "\\u005c";
    private static final HexFormat HEX = HexFormat.of();

    private UnicodeEscapes() {}

    /**
     * Returns the source with every character after U+007F written as a Unicode escape: each UTF-16 unit of a
     * supplementary character is escaped on its own, as Java sources write them.
     *
     * @param source the source text
     * @return the same source in ASCII only, which javac translates back to {@code source}; {@code source} itself if it
     *     is ASCII already
     */
    public static String escapeNonAscii(String source) {
        if (source.chars().allMatch(character -> character <= LAST_ASCII)) {
            return source;
        }
        StringBuilder escaped = new StringBuilder(source.length() + source.length() / 2);
        int backslashes = 0;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character <= LAST_ASCII) {
                escaped.append(character);
                backslashes = character == '\\' ? backslashes + 1 : 0;
                continue;
            }
            if (backslashes % 2 == 1) {
                escaped.setLength(escaped.length() - 1);
                escaped.append(ESCAPED_BACKSLASH);
            }
            escaped.append("\\u").append(HEX.toHexDigits(character));
            backslashes = 0;
        }
        return escaped.toString();
    }
}
