package be.imgn.gaia.core.io.internal;

import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

/**
 * A stream that decodes the bytes written to it into characters for a writer: the reverse of an
 * {@link java.io.OutputStreamWriter}. The decoder decides what to do with bytes it cannot decode. Bytes that end in the
 * middle of a character wait for the next ones, up to {@link #close()}.
 */
public final class DecodingOutputStream extends OutputStream {

    private final Writer writer;
    private final CharsetDecoder decoder;
    // Both buffers are kept ready to be written to.
    private final ByteBuffer bytes = ByteBuffer.allocate(Streams.BUFFER_SIZE);
    private final CharBuffer chars = CharBuffer.allocate(Streams.BUFFER_SIZE);
    private boolean closed;
    private boolean failed;

    /**
     * Creates a stream that decodes the bytes written to it into characters for a writer.
     *
     * @param writer the writer to write the characters to, closed with this stream
     * @param decoder the decoder to use, new or reset
     */
    public DecodingOutputStream(Writer writer, CharsetDecoder decoder) {
        this.writer = requireNonNull(writer, "writer");
        this.decoder = requireNonNull(decoder, "decoder");
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        checkFromIndexSize(offset, length, buffer.length);
        if (closed) {
            throw new IOException("Stream closed");
        }
        var position = offset;
        var end = offset + length;
        while (position < end) {
            var count = Math.min(end - position, bytes.remaining());
            bytes.put(buffer, position, count);
            position += count;
            decode(false);
        }
    }

    // Bytes waiting for the rest of their character stay here: the writer only receives whole characters.
    @Override
    public void flush() throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try (writer) {
            // The invalid bytes are still buffered and were already reported: decoding them again would fail again.
            if (failed) {
                return;
            }
            decode(true);
            CoderResult result;
            do {
                result = decoder.flush(chars);
                writeChars();
            } while (result.isOverflow());
        }
    }

    // On a coding error, the characters decoded before the invalid bytes are written, and the buffer is compacted
    // all the same, so that no byte is ever decoded twice.
    private void decode(boolean endOfInput) throws IOException {
        bytes.flip();
        try {
            CoderResult result;
            do {
                result = decoder.decode(bytes, chars, endOfInput);
                writeChars();
                if (result.isError()) {
                    failed = true;
                    result.throwException();
                }
            } while (result.isOverflow());
        } finally {
            bytes.compact();
        }
    }

    // Cleared even when the writer fails: it may have written some of the characters, which must not come again.
    private void writeChars() throws IOException {
        chars.flip();
        try {
            writer.write(chars.array(), chars.arrayOffset() + chars.position(), chars.remaining());
        } finally {
            chars.clear();
        }
    }
}
