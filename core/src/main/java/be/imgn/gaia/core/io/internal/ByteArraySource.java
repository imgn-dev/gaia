package be.imgn.gaia.core.io.internal;

import be.imgn.gaia.core.io.ByteSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;
import java.util.OptionalLong;

/** A source that reads an array, which it does not copy. */
public final class ByteArraySource implements ByteSource {

    /** The source with no bytes. */
    public static final ByteSource EMPTY = new ByteArraySource(new byte[0]);

    private final byte[] bytes;

    /**
     * Creates a source that reads an array.
     *
     * @param bytes the array to read
     */
    public ByteArraySource(byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes");
    }

    @Override
    public InputStream openStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public OptionalLong sizeIfKnown() {
        return OptionalLong.of(bytes.length);
    }
}
