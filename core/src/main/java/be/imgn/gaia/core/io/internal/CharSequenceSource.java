package be.imgn.gaia.core.io.internal;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.io.CharSource;
import java.io.Reader;
import java.io.StringReader;
import java.util.OptionalLong;

/** A source that reads a character sequence, which it does not copy. */
public final class CharSequenceSource implements CharSource {

    /** The source with no characters. */
    public static final CharSource EMPTY = new CharSequenceSource("");

    private final CharSequence chars;

    /**
     * Creates a source that reads a character sequence.
     *
     * @param chars the sequence to read
     */
    public CharSequenceSource(CharSequence chars) {
        this.chars = requireNonNull(chars, "chars");
    }

    @Override
    public Reader openStream() {
        return new StringReader(chars.toString());
    }

    @Override
    public OptionalLong lengthIfKnown() {
        return OptionalLong.of(chars.length());
    }

    @Override
    public String read() {
        return chars.toString();
    }
}
