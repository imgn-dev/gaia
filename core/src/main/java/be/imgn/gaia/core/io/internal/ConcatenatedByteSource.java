package be.imgn.gaia.core.io.internal;

import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.io.ByteSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/** A source that reads several sources one after the other. */
public final class ConcatenatedByteSource implements ByteSource {

    private final List<ByteSource> sources;

    /**
     * Creates a source that reads several sources one after the other.
     *
     * @param sources the sources to read, copied
     */
    public ConcatenatedByteSource(Iterable<? extends ByteSource> sources) {
        var list = new ArrayList<ByteSource>();
        for (ByteSource source : requireNonNull(sources, "sources")) {
            list.add(requireNonNull(source, "sources contains null"));
        }
        this.sources = List.copyOf(list);
    }

    @Override
    public InputStream openStream() {
        return new ConcatenatedInputStream(sources.iterator());
    }

    @Override
    public boolean isEmpty() throws IOException {
        for (var source : sources) {
            if (!source.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public OptionalLong sizeIfKnown() {
        var total = 0L;
        for (var source : sources) {
            var size = source.sizeIfKnown();
            if (size.isEmpty() || size.getAsLong() > Long.MAX_VALUE - total) {
                return OptionalLong.empty();
            }
            total += size.getAsLong();
        }
        return OptionalLong.of(total);
    }

    private static final class ConcatenatedInputStream extends InputStream {

        private final Iterator<ByteSource> sources;
        private @Nullable InputStream current;
        private boolean closed;

        ConcatenatedInputStream(Iterator<ByteSource> sources) {
            this.sources = sources;
        }

        @Override
        public int read() throws IOException {
            for (var in = current(); in != null; in = next()) {
                var read = in.read();
                if (read != -1) {
                    return read;
                }
            }
            return -1;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return 0;
            }
            for (var in = current(); in != null; in = next()) {
                var read = in.read(bytes, offset, length);
                if (read != -1) {
                    return read;
                }
            }
            return -1;
        }

        @Override
        public int available() throws IOException {
            var in = current;
            return in == null ? 0 : in.available();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            var in = current;
            current = null;
            if (in != null) {
                in.close();
            }
        }

        // The stream being read, opened if none is, or null past the last source.
        private @Nullable InputStream current() throws IOException {
            if (closed) {
                throw new IOException("Stream closed");
            }
            var in = current;
            if (in == null && sources.hasNext()) {
                in = sources.next().openStream();
                current = in;
            }
            return in;
        }

        // Closes the stream read to its end and opens the next one.
        private @Nullable InputStream next() throws IOException {
            var in = current;
            current = null;
            if (in != null) {
                in.close();
            }
            return current();
        }
    }
}
