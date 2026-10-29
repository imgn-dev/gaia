package be.imgn.gaia.core.io;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.base.Charsets;
import be.imgn.gaia.core.io.internal.DecodingOutputStream;
import be.imgn.gaia.core.io.internal.Streams;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * A writable destination of characters, such as a text file, that opens a new stream each time it is written.
 *
 * <p>Unlike a {@link Writer}, a sink is not an open resource: it can be kept, passed around and written any number of
 * times, and each write method opens a stream and closes it before returning. Only {@link #openStream()} and
 * {@link #openBufferedStream()} leave a stream for the caller to close.
 *
 * <p>The only abstract method is {@link #openStream()}, so a lambda makes a sink:
 *
 * <pre>{@code
 * CharSink sink = () -> new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
 * CharSink.of(file, StandardCharsets.UTF_8).writeLines(lines);
 * }</pre>
 *
 * @since 0.1.1
 */
@FunctionalInterface
public interface CharSink {

    /**
     * Returns a sink that writes a text file in the {@linkplain Charsets#DEFAULT_CHARSET current default charset}. By
     * default, each write creates the file or replaces its content, but it is not atomic: see {@link AtomicFiles} for
     * that.
     *
     * @param file the file to write
     * @param options how to open the file, as for {@link java.nio.file.Files#newOutputStream(Path, OpenOption...)}
     * @return a sink that writes {@code file}
     * @see #of(Path, Charset, OpenOption...)
     * @since 0.1.2
     */
    static CharSink of(Path file, OpenOption... options) {
        return of(file, Charsets.DEFAULT_CHARSET, options);
    }

    /**
     * Returns a sink that writes a text file. By default, each write creates the file or replaces its content, but it
     * is not atomic: see {@link AtomicFiles} for that.
     *
     * @param file the file to write
     * @param charset the charset to encode the text in
     * @param options how to open the file, as for {@link java.nio.file.Files#newOutputStream(Path, OpenOption...)}
     * @return a sink that writes {@code file}
     * @throws IllegalArgumentException if {@code charset} does not support encoding
     * @see ByteSink#asCharSink(Charset)
     */
    static CharSink of(Path file, Charset charset, OpenOption... options) {
        return ByteSink.of(file, options).asCharSink(charset);
    }

    /**
     * Opens a new stream to write the characters of this sink. The caller must close it.
     *
     * @return a new stream
     * @throws IOException if the stream cannot be opened
     */
    Writer openStream() throws IOException;

    /**
     * Opens a new buffered stream to write the characters of this sink. The caller must close it.
     *
     * @return a new buffered stream
     * @throws IOException if the stream cannot be opened
     */
    default Writer openBufferedStream() throws IOException {
        var out = openStream();
        return out instanceof BufferedWriter ? out : new BufferedWriter(out);
    }

    /**
     * Writes characters to this sink.
     *
     * @param chars the characters to write
     * @throws IOException if this sink cannot be written
     */
    default void write(CharSequence chars) throws IOException {
        requireNonNull(chars, "chars");
        try (var out = openStream()) {
            out.append(chars);
        }
    }

    /**
     * Writes lines to this sink, each followed by the line separator of the system.
     *
     * @param lines the lines to write
     * @throws IOException if this sink cannot be written
     * @see System#lineSeparator()
     */
    default void writeLines(Iterable<? extends CharSequence> lines) throws IOException {
        requireNonNull(lines, "lines");
        writeLines(lines, System.lineSeparator());
    }

    /**
     * Writes lines to this sink, each followed by a separator.
     *
     * @param lines the lines to write
     * @param separator the separator to write after each line
     * @throws IOException if this sink cannot be written
     */
    default void writeLines(Iterable<? extends CharSequence> lines, String separator) throws IOException {
        requireNonNull(lines, "lines");
        requireNonNull(separator, "separator");
        // Checked before the sink is opened, which would already replace the content of a file, and copied since an
        // iterable may not be iterated twice.
        var copy = new ArrayList<CharSequence>();
        for (CharSequence line : lines) {
            copy.add(requireNonNull(line, "lines contains null"));
        }
        writeLines(copy.iterator(), separator);
    }

    /**
     * Writes the lines of a stream to this sink, each followed by the line separator of the system. The stream is
     * consumed but left open. Its lines are checked as they are written, so a null line leaves the lines before it
     * written.
     *
     * @param lines the lines to write
     * @throws IOException if this sink cannot be written
     * @see System#lineSeparator()
     */
    default void writeLines(Stream<? extends CharSequence> lines) throws IOException {
        requireNonNull(lines, "lines");
        writeLines(lines, System.lineSeparator());
    }

    /**
     * Writes the lines of a stream to this sink, each followed by a separator. The stream is consumed but left open.
     * Its lines are checked as they are written, so a null line leaves the lines before it written.
     *
     * @param lines the lines to write
     * @param separator the separator to write after each line
     * @throws IOException if this sink cannot be written
     */
    default void writeLines(Stream<? extends CharSequence> lines, String separator) throws IOException {
        requireNonNull(lines, "lines");
        requireNonNull(separator, "separator");
        writeLines(lines.iterator(), separator);
    }

    // A stream can only be read once, so its lines are checked as they are written.
    private void writeLines(Iterator<? extends CharSequence> lines, String separator) throws IOException {
        try (var out = openBufferedStream()) {
            while (lines.hasNext()) {
                out.append(requireNonNull(lines.next(), "lines contains null")).append(separator);
            }
        }
    }

    /**
     * Writes the characters of a readable to this sink, up to its end. The readable is left open.
     *
     * @param readable the readable to read
     * @return the number of characters written
     * @throws IOException if {@code readable} cannot be read or this sink cannot be written
     */
    default long writeFrom(Readable readable) throws IOException {
        requireNonNull(readable, "readable");
        try (var out = openStream()) {
            return Streams.copy(readable, out);
        }
    }

    /**
     * Returns a sink that writes bytes to this one, decoded from the {@linkplain Charsets#DEFAULT_CHARSET current
     * default charset}. Writing to it fails with a {@link java.nio.charset.CharacterCodingException} on bytes that are
     * not valid in that charset, rather than replace them. Bytes that end in the middle of a character are reported
     * when the stream is closed.
     *
     * @return a sink of bytes
     * @see #asByteSink(Charset)
     * @since 0.1.2
     */
    default ByteSink asByteSink() {
        return asByteSink(Charsets.DEFAULT_CHARSET);
    }

    /**
     * Returns a sink that writes bytes to this one, decoded from a charset. Writing to it fails with a
     * {@link java.nio.charset.CharacterCodingException} on bytes that are not valid in {@code charset}, rather than
     * replace them. Bytes that end in the middle of a character are reported when the stream is closed.
     *
     * @param charset the charset the bytes are encoded in
     * @return a sink of bytes
     */
    default ByteSink asByteSink(Charset charset) {
        requireNonNull(charset, "charset");
        return () -> new DecodingOutputStream(openStream(), charset.newDecoder());
    }
}
