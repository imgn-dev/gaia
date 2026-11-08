package be.imgn.gaia.core.io;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.base.Charsets;
import be.imgn.gaia.core.io.internal.OpenOptions;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/**
 * A writable destination of bytes, such as a file, that opens a new writer each time it is written.
 *
 * <p>Unlike an {@link OutputStream}, a sink is not an open resource: it can be kept, passed around and written any
 * number of times, and each write method opens a writer and closes it before returning. Only {@link #openWriter()}
 * leaves a writer for the caller to close.
 *
 * <p>The only abstract method is {@link #openWriter()}, so a lambda makes a sink:
 *
 * <pre>{@code
 * ByteSink sink = () -> ByteWriter.of(socket.getOutputStream());
 * ByteSink.of(file).write(bytes);
 * ByteSink.of(log, StandardOpenOption.CREATE, StandardOpenOption.APPEND).write(entry);
 * }</pre>
 *
 * @since 0.1.1
 */
@FunctionalInterface
public interface ByteSink {

    /**
     * Returns a sink that writes a file. By default, each write creates the file or replaces its content, but it is not
     * atomic: see {@link AtomicFiles} for that.
     *
     * @param file the file to write
     * @param options how to open the file, as for {@link Files#newOutputStream(Path, OpenOption...)}
     * @return a sink that writes {@code file}
     */
    static ByteSink of(Path file, OpenOption... options) {
        requireNonNull(file, "file");
        var copy = OpenOptions.copyOf(options);
        return () -> ByteWriter.of(Files.newOutputStream(file, copy));
    }

    /**
     * Opens a new writer to write the bytes of this sink. The caller must close it. For APIs that take an
     * {@link OutputStream}, {@link ByteWriter#asOutputStream()} writes through it.
     *
     * @return a new writer
     * @throws IOException if the writer cannot be opened
     * @since 0.1.3
     */
    ByteWriter openWriter() throws IOException;

    /**
     * Writes bytes to this sink.
     *
     * @param bytes the bytes to write
     * @throws IOException if this sink cannot be written
     */
    default void write(byte[] bytes) throws IOException {
        requireNonNull(bytes, "bytes");
        try (var out = openWriter()) {
            out.write(bytes);
        }
    }

    /**
     * Writes bytes to this sink.
     *
     * @param bytes the bytes to write
     * @throws IOException if this sink cannot be written
     * @since 0.1.3
     */
    default void write(Bytes bytes) throws IOException {
        requireNonNull(bytes, "bytes");
        try (var out = openWriter()) {
            out.write(bytes);
        }
    }

    /**
     * Writes the bytes of a stream to this sink, up to its end. The stream is left open.
     *
     * @param in the stream to read
     * @return the number of bytes written
     * @throws IOException if {@code in} cannot be read or this sink cannot be written
     */
    default long writeFrom(InputStream in) throws IOException {
        requireNonNull(in, "in");
        try (var out = openWriter()) {
            return out.transferFrom(in);
        }
    }

    /**
     * Returns a sink that writes characters to this one, encoded in the {@linkplain Charsets#DEFAULT_CHARSET current
     * default charset}. Writing to it fails with a {@link java.nio.charset.CharacterCodingException} on characters that
     * charset cannot encode, or on a malformed surrogate pair, rather than replace them.
     *
     * @return a sink of characters
     * @see #asCharSink(Charset)
     * @since 0.1.2
     */
    default CharSink asCharSink() {
        return asCharSink(Charsets.DEFAULT_CHARSET);
    }

    /**
     * Returns a sink that writes characters to this one, encoded in a charset. Writing to it fails with a
     * {@link java.nio.charset.CharacterCodingException} on characters {@code charset} cannot encode, or on a malformed
     * surrogate pair, rather than replace them.
     *
     * @param charset the charset to encode the characters in
     * @return a sink of characters
     * @throws IllegalArgumentException if {@code charset} does not support encoding
     */
    default CharSink asCharSink(Charset charset) {
        requireNonNull(charset, "charset");
        checkArgument(charset.canEncode(), "%s does not support encoding", charset);
        return () -> CharWriter.of(new EncodingWriter(openWriter(), charset.newEncoder()));
    }
}
