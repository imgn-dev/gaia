package be.imgn.gaia.core.io;

import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

/**
 * A stream of the characters that the bytes of a reader encode. Unlike an {@link java.io.InputStreamReader}, it decodes
 * the bytes where the reader buffers them rather than copy them to a buffer of its own. The decoder decides what to do
 * with bytes it cannot decode.
 */
final class DecodingReader extends Reader {

    private final ByteReader in;
    private final CharsetDecoder decoder;
    // The second character decoded for a caller that had room for only one, since a surrogate pair needs two.
    private int leftover = -1;
    private boolean endOfInput;
    private boolean decoded;
    private boolean flushed;

    /**
     * Creates a stream of the characters that the bytes of a reader encode.
     *
     * @param in the reader to decode, closed with this stream
     * @param decoder the decoder to use, new or reset
     */
    DecodingReader(ByteReader in, CharsetDecoder decoder) {
        this.in = requireNonNull(in, "in");
        this.decoder = requireNonNull(decoder, "decoder");
    }

    @Override
    public int read(char[] chars, int offset, int length) throws IOException {
        checkFromIndexSize(offset, length, chars.length);
        if (length == 0) {
            return 0;
        }
        if (leftover != -1) {
            chars[offset] = (char) leftover;
            leftover = -1;
            return 1;
        }
        if (length > 1) {
            return decode(CharBuffer.wrap(chars, offset, length));
        }
        var pair = new char[2];
        var read = decode(CharBuffer.wrap(pair));
        if (read > 0) {
            chars[offset] = pair[0];
        }
        if (read == 2) {
            leftover = pair[1];
        }
        return Math.min(read, 1);
    }

    @Override
    public void close() throws IOException {
        leftover = -1;
        in.close();
    }

    // Decodes at least one character unless the bytes end, and reads bytes only when those buffered don't make one.
    private int decode(CharBuffer out) throws IOException {
        var start = out.position();
        var needed = 1;
        while (out.position() == start) {
            if (flushed) {
                return -1;
            }
            if (!endOfInput) {
                endOfInput = !in.request(needed);
            }
            if (!decoded) {
                var bytes = in.buffered();
                CoderResult result;
                try {
                    result = decoder.decode(bytes, out, endOfInput);
                } finally {
                    in.consume(bytes);
                }
                // The characters decoded before the invalid bytes are read first: the error comes again after them.
                if (result.isError() && out.position() == start) {
                    result.throwException();
                }
                decoded = endOfInput && result.isUnderflow();
                // The bytes left start a character that the next ones end.
                needed = bytes.remaining() + 1;
            }
            if (decoded) {
                flushed = decoder.flush(out).isUnderflow();
            }
        }
        return out.position() - start;
    }
}
