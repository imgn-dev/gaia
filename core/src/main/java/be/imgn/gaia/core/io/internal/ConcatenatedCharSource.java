package be.imgn.gaia.core.io.internal;

import be.imgn.gaia.core.io.CharSource;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/** A source that reads several sources one after the other. */
public final class ConcatenatedCharSource implements CharSource {

    private final List<CharSource> sources;

    /**
     * Creates a source that reads several sources one after the other.
     *
     * @param sources the sources to read, copied
     */
    public ConcatenatedCharSource(Iterable<? extends CharSource> sources) {
        var list = new ArrayList<CharSource>();
        sources.forEach(list::add);
        this.sources = List.copyOf(list);
    }

    @Override
    public Reader openStream() {
        return new ConcatenatedReader(sources.iterator());
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
    public OptionalLong lengthIfKnown() {
        var total = 0L;
        for (var source : sources) {
            var length = source.lengthIfKnown();
            if (length.isEmpty() || length.getAsLong() > Long.MAX_VALUE - total) {
                return OptionalLong.empty();
            }
            total += length.getAsLong();
        }
        return OptionalLong.of(total);
    }

    private static final class ConcatenatedReader extends Reader {

        private final Iterator<CharSource> sources;
        private @Nullable Reader current;
        private boolean closed;

        ConcatenatedReader(Iterator<CharSource> sources) {
            this.sources = sources;
        }

        @Override
        public int read(char[] chars, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, chars.length);
            if (length == 0) {
                return 0;
            }
            for (var in = current(); in != null; in = next()) {
                var read = in.read(chars, offset, length);
                if (read != -1) {
                    return read;
                }
            }
            return -1;
        }

        @Override
        public boolean ready() throws IOException {
            var in = current;
            return in != null && in.ready();
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
        private @Nullable Reader current() throws IOException {
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
        private @Nullable Reader next() throws IOException {
            var in = current;
            current = null;
            if (in != null) {
                in.close();
            }
            return current();
        }
    }
}
