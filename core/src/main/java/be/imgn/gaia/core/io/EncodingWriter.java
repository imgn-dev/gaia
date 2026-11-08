package be.imgn.gaia.core.io;

import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.Writer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

/**
 * A stream that encodes the characters written to it for a writer. Unlike an {@link java.io.OutputStreamWriter}, it
 * encodes them where the writer buffers bytes rather than in a buffer of its own. The encoder decides what to do with
 * characters it cannot encode. A high surrogate that ends a write waits for the low surrogate of the next one, up to
 * {@link #close()}.
 */
final class EncodingWriter extends Writer {

    private final ByteWriter out;
    private final CharsetEncoder encoder;
    private int leftover = -1;
    private boolean closed;

    /**
     * Creates a stream that encodes the characters written to it for a writer.
     *
     * @param out the writer to write the bytes to, closed with this stream
     * @param encoder the encoder to use, new or reset
     */
    EncodingWriter(ByteWriter out, CharsetEncoder encoder) {
        this.out = requireNonNull(out, "out");
        this.encoder = requireNonNull(encoder, "encoder");
    }

    @Override
    public void write(char[] chars, int offset, int length) throws IOException {
        checkFromIndexSize(offset, length, chars.length);
        ensureOpen();
        var in = CharBuffer.wrap(chars, offset, length);
        if (leftover != -1 && in.hasRemaining()) {
            var pair = CharBuffer.wrap(new char[] {(char) leftover, in.get()});
            leftover = -1;
            encode(pair, false);
        }
        encode(in, false);
        if (in.hasRemaining()) {
            leftover = in.get();
        }
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try (out) {
            var in = leftover == -1 ? CharBuffer.allocate(0) : CharBuffer.wrap(new char[] {(char) leftover});
            leftover = -1;
            encode(in, true);
            CoderResult result;
            var needed = 1;
            do {
                var bytes = out.room(needed);
                try {
                    result = encoder.flush(bytes);
                } finally {
                    out.commit(bytes);
                }
                needed = bytes.remaining() + 1;
            } while (result.isOverflow());
        }
    }

    // Encodes into the buffer of the writer, which is flushed whenever the encoder needs more room than is left.
    private void encode(CharBuffer in, boolean endOfInput) throws IOException {
        CoderResult result;
        var needed = 1;
        do {
            var bytes = out.room(needed);
            try {
                result = encoder.encode(in, bytes, endOfInput);
            } finally {
                out.commit(bytes);
            }
            if (result.isError()) {
                result.throwException();
            }
            needed = bytes.remaining() + 1;
        } while (result.isOverflow());
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
    }
}
