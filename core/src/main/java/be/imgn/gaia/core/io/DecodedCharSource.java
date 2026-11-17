package be.imgn.gaia.core.io;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * The characters that the bytes of a source encode, as {@link ByteSource#asCharSource(Charset)} returns them. Its
 * readers decode the bytes as they read them. {@link #read()} reads bytes up to {@link #WHOLE_LIMIT} whole first and
 * decodes them at once, which is much faster; it decodes more as a stream. Every way gives the same text, and fails
 * alike on bytes that are not valid in the charset.
 */
final class DecodedCharSource implements CharSource {

    // Bytes up to this many are read whole, then decoded at once. Past it, decoding as a stream is faster, as it works
    // on runs that stay in the processor's caches, and takes less memory: the bytes and the text at once, with the
    // array twice the size of the bytes that the JDK decodes UTF-8 into before it knows whether the text is Latin-1,
    // take more than a StringBuilder that grows as the text is decoded. TextReadBenchmark, on Apple Silicon with
    // 12 MiB of L2 cache per cluster of cores, found decoding at once about twice as fast for ASCII up to 4 MiB, and
    // slower from 8 MiB; for mixed text, slightly faster up to 8 MiB, and twice as slow at 16 MiB. A processor with
    // a smaller cache may need a smaller limit.
    static final int WHOLE_LIMIT = 4 << 20;

    private final ByteSource bytes;
    private final Charset charset;

    /**
     * Creates the source of the characters that the bytes of a source encode.
     *
     * @param bytes the source of the bytes
     * @param charset the charset the bytes are encoded in
     */
    DecodedCharSource(ByteSource bytes, Charset charset) {
        this.bytes = requireNonNull(bytes, "bytes");
        this.charset = requireNonNull(charset, "charset");
    }

    @Override
    public CharReader openReader() throws IOException {
        return CharReader.of(new DecodingReader(bytes.openReader(), charset.newDecoder()));
    }

    @Override
    public String read() throws IOException {
        var size = bytes.sizeIfKnown();
        if (size.isPresent()) {
            if (size.getAsLong() > WHOLE_LIMIT) {
                return CharSource.super.read();
            }
            return decode(ByteReader.of(bytes.read()));
        }
        // Reads ahead up to the limit, which tells whether the bytes are within it.
        try (var in = bytes.openReader()) {
            if (in.request(WHOLE_LIMIT + 1)) {
                return decodeAsAStream(in);
            }
            return decode(in);
        }
    }

    // Decodes the bytes that a reader has buffered, which are all it has.
    private String decode(ByteReader in) throws IOException {
        var buffered = in.buffered();
        var text = decodeIfValid(buffered.array(), buffered.arrayOffset() + buffered.position(), buffered.remaining());
        if (text != null) {
            return text;
        }
        // Decodes the bytes again, as a stream, so that the error is the one that reading them as a stream reports.
        return decodeAsAStream(in);
    }

    private String decodeAsAStream(ByteReader in) throws IOException {
        try (var chars = CharReader.of(new DecodingReader(in, charset.newDecoder()))) {
            return chars.readAll();
        }
    }

    // Decodes with the JDK's own decoding, the fastest there is, in the charsets where it replaces invalid bytes with
    // U+FFFD: then a text without U+FFFD is valid. Returns null if the text may not be.
    private @Nullable String decodeIfValid(byte[] array, int offset, int length) {
        if (charset.equals(StandardCharsets.ISO_8859_1)) {
            // Every byte is valid.
            return new String(array, offset, length, charset);
        }
        if (charset.equals(StandardCharsets.UTF_8) || charset.equals(StandardCharsets.US_ASCII)) {
            var text = new String(array, offset, length, charset);
            return text.indexOf('�') < 0 ? text : null;
        }
        return null;
    }
}
