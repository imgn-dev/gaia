package be.imgn.gaia.core.io.internal;

import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.io.ByteSource;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.OptionalLong;

/** A source that reads a part of another one. */
public final class SlicedByteSource implements ByteSource {

    private final ByteSource source;
    private final long offset;
    private final long length;

    /**
     * Creates a source that reads at most {@code length} bytes of {@code source}, from {@code offset} on.
     *
     * @param source the source to read a part of
     * @param offset the number of bytes to skip, not negative
     * @param length the maximum number of bytes to read, not negative
     */
    public SlicedByteSource(ByteSource source, long offset, long length) {
        this.source = requireNonNull(source, "source");
        this.offset = offset;
        this.length = length;
    }

    @Override
    public InputStream openStream() throws IOException {
        var in = source.openStream();
        var skipped = false;
        try {
            Streams.skipUpTo(in, offset);
            skipped = true;
        } finally {
            if (!skipped) {
                in.close();
            }
        }
        return new LimitedInputStream(in, length);
    }

    @Override
    public OptionalLong sizeIfKnown() {
        var size = source.sizeIfKnown();
        if (size.isEmpty()) {
            return size;
        }
        return OptionalLong.of(Math.min(length, Math.max(0, size.getAsLong() - offset)));
    }

    // Mark and reset are not supported: they would have to restore the count of bytes left too.
    private static final class LimitedInputStream extends FilterInputStream {

        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                return -1;
            }
            var read = in.read();
            if (read != -1) {
                remaining--;
            }
            return read;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return 0;
            }
            if (remaining == 0) {
                return -1;
            }
            var read = in.read(bytes, offset, (int) Math.min(length, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public long skip(long count) throws IOException {
            // Some streams, such as a FileInputStream, seek backwards on a negative count, out of the slice.
            if (count <= 0) {
                return 0;
            }
            var skipped = in.skip(Math.min(count, remaining));
            remaining -= skipped;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(in.available(), remaining);
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public synchronized void mark(int limit) {}

        @Override
        public synchronized void reset() throws IOException {
            throw new IOException("mark/reset not supported");
        }
    }
}
