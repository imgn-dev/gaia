package be.imgn.gaia.core.io;

import static java.util.Objects.checkFromIndexSize;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.function.IntSupplier;

/** Streams for the tests of readers and writers. */
final class TestStreams {

    private TestStreams() {}

    /**
     * Returns a value made from each way a stream can return data: all at once, one by one, and in chunks of random
     * sizes, so that values and line terminators are split across the fills of a buffer.
     */
    static <T> List<Named<T>> eachChunking(Function<IntSupplier, T> factory) {
        var random = new SplittableRandom(42);
        return List.of(
                new Named<>("all at once", factory.apply(() -> Integer.MAX_VALUE)),
                new Named<>("one by one", factory.apply(() -> 1)),
                new Named<>("random chunks", factory.apply(() -> random.nextInt(1, 100))));
    }

    static byte[] randomBytes(long seed, int count) {
        var bytes = new byte[count];
        new SplittableRandom(seed).nextBytes(bytes);
        return bytes;
    }

    /** A value under test, with a name for assertion messages. */
    record Named<T>(String name, T value) {}

    /** A stream on bytes that returns at most a few per read, and counts how many times it is closed. */
    static final class ChunkedInputStream extends InputStream {

        private final byte[] bytes;
        private final IntSupplier chunk;
        private int position;
        private int closeCount;

        ChunkedInputStream(byte[] bytes, IntSupplier chunk) {
            this.bytes = bytes;
            this.chunk = chunk;
        }

        @Override
        public int read() {
            return position < bytes.length ? Byte.toUnsignedInt(bytes[position++]) : -1;
        }

        @Override
        public int read(byte[] b, int offset, int length) {
            checkFromIndexSize(offset, length, b.length);
            if (length == 0) {
                return 0;
            }
            if (position == bytes.length) {
                return -1;
            }
            var count = Math.min(Math.min(length, chunk.getAsInt()), bytes.length - position);
            System.arraycopy(bytes, position, b, offset, count);
            position += count;
            return count;
        }

        @Override
        public void close() {
            closeCount++;
        }

        int closeCount() {
            return closeCount;
        }
    }

    /** A stream on characters that returns at most a few per read, and counts how many times it is closed. */
    static final class ChunkedReader extends Reader {

        private final String chars;
        private final IntSupplier chunk;
        private int position;
        private int closeCount;

        ChunkedReader(String chars, IntSupplier chunk) {
            this.chars = chars;
            this.chunk = chunk;
        }

        @Override
        public int read(char[] b, int offset, int length) {
            checkFromIndexSize(offset, length, b.length);
            if (length == 0) {
                return 0;
            }
            if (position == chars.length()) {
                return -1;
            }
            var count = Math.min(Math.min(length, chunk.getAsInt()), chars.length() - position);
            chars.getChars(position, position + count, b, offset);
            position += count;
            return count;
        }

        @Override
        public void close() {
            closeCount++;
        }

        int closeCount() {
            return closeCount;
        }
    }

    /** A stream that fails to write and to close, with messages "write" and "close". */
    static final class FailingOutputStream extends OutputStream {

        private int closeCount;

        @Override
        public void write(int b) throws IOException {
            throw new IOException("write");
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            throw new IOException("close");
        }

        int closeCount() {
            return closeCount;
        }
    }

    /** A stream that fails to write and to close, with messages "write" and "close". */
    static final class FailingWriter extends Writer {

        private int closeCount;

        @Override
        public void write(char[] b, int offset, int length) throws IOException {
            throw new IOException("write");
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws IOException {
            closeCount++;
            throw new IOException("close");
        }

        int closeCount() {
            return closeCount;
        }
    }
}
