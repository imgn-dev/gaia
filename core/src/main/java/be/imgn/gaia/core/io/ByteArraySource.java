package be.imgn.gaia.core.io;

import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.requireNonNull;

import java.util.OptionalLong;

/** A source that reads an array, or part of one, which it does not copy. */
final class ByteArraySource implements ByteSource {

    /** The source with no bytes. */
    static final ByteSource EMPTY = new ByteArraySource(new byte[0]);

    private final byte[] bytes;
    private final int offset;
    private final int length;

    /**
     * Creates a source that reads an array.
     *
     * @param bytes the array to read
     */
    ByteArraySource(byte[] bytes) {
        this(requireNonNull(bytes, "bytes"), 0, bytes.length);
    }

    /**
     * Creates a source that reads part of an array.
     *
     * @param bytes the array to read
     * @param offset the index of the first byte to read
     * @param length the number of bytes to read
     * @throws IndexOutOfBoundsException if {@code offset} or {@code length} is negative, or if {@code offset + length}
     *     exceeds the length of {@code bytes}
     */
    ByteArraySource(byte[] bytes, int offset, int length) {
        this.bytes = requireNonNull(bytes, "bytes");
        this.offset = checkFromIndexSize(offset, length, bytes.length);
        this.length = length;
    }

    @Override
    public ByteReader openReader() {
        return ByteReader.of(Bytes.wrap(bytes, offset, length));
    }

    @Override
    public OptionalLong sizeIfKnown() {
        return OptionalLong.of(length);
    }
}
