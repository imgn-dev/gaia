package be.imgn.gaia.core.io.internal;

import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

/**
 * A stream of the bytes that encode the characters of a reader: the reverse of an {@link java.io.InputStreamReader}.
 * The encoder decides what to do with characters it cannot encode.
 */
public final class EncodingInputStream extends InputStream {

    private final Reader reader;
    private final CharsetEncoder encoder;
    // Both buffers are kept ready to be read from: flipped after being filled.
    private final CharBuffer chars = CharBuffer.allocate(Streams.BUFFER_SIZE).flip();
    private final ByteBuffer bytes = ByteBuffer.allocate(Streams.BUFFER_SIZE).flip();
    private boolean endOfInput;
    private boolean encoded;
    private boolean flushed;

    /**
     * Creates a stream of the bytes that encode the characters of a reader.
     *
     * @param reader the reader to encode, closed with this stream
     * @param encoder the encoder to use, new or reset
     */
    public EncodingInputStream(Reader reader, CharsetEncoder encoder) {
        this.reader = requireNonNull(reader, "reader");
        this.encoder = requireNonNull(encoder, "encoder");
    }

    @Override
    public int read() throws IOException {
        return fill() ? Byte.toUnsignedInt(bytes.get()) : -1;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        checkFromIndexSize(offset, length, buffer.length);
        if (length == 0) {
            return 0;
        }
        if (!fill()) {
            return -1;
        }
        var read = Math.min(length, bytes.remaining());
        bytes.get(buffer, offset, read);
        return read;
    }

    @Override
    public int available() {
        return bytes.remaining();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    // Encodes more characters if no encoded byte is left, and tells whether there is one now.
    private boolean fill() throws IOException {
        while (!bytes.hasRemaining() && !flushed) {
            bytes.clear();
            encodeMore();
            bytes.flip();
        }
        return bytes.hasRemaining();
    }

    private void encodeMore() throws IOException {
        if (!endOfInput) {
            chars.compact();
            endOfInput = reader.read(chars) == -1;
            chars.flip();
        }
        if (!encoded) {
            var result = encoder.encode(chars, bytes, endOfInput);
            check(result);
            encoded = endOfInput && result.isUnderflow();
        }
        if (encoded) {
            var result = encoder.flush(bytes);
            check(result);
            flushed = result.isUnderflow();
        }
    }

    private static void check(CoderResult result) throws IOException {
        if (result.isError()) {
            result.throwException();
        }
    }
}
