package be.imgn.gaia.core.io;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.io.internal.CharSequenceSource;
import be.imgn.gaia.core.io.internal.ConcatenatedCharSource;
import be.imgn.gaia.core.io.internal.EncodingInputStream;
import be.imgn.gaia.core.io.internal.Streams;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A readable source of characters, such as a text file or a string, that opens a new stream each time it is read.
 *
 * <p>Unlike a {@link Reader}, a source is not an open resource: it can be kept, passed around and read any number of
 * times, and each read method opens a stream and closes it before returning. Only {@link #openStream()},
 * {@link #openBufferedStream()} and {@link #lines()} leave a stream for the caller to close.
 *
 * <p>The only abstract method is {@link #openStream()}, so a lambda makes a source:
 *
 * <pre>{@code
 * CharSource source = () -> new InputStreamReader(System.in, StandardCharsets.UTF_8);
 * List<String> lines = CharSource.of(file, StandardCharsets.UTF_8).readLines();
 * }</pre>
 *
 * <p>Lines end at a line feed ({@code \n}), a carriage return ({@code \r}), or a carriage return followed by a line
 * feed, as with {@link BufferedReader#readLine()}. Lines never include their line terminator.
 *
 * <p>Implementations may override the other methods with faster ones, such as {@link #lengthIfKnown()} for a source
 * that knows its length without reading it.
 *
 * @since 0.1.1
 */
@FunctionalInterface
public interface CharSource {

    /**
     * Returns a source with no characters.
     *
     * @return an empty source
     */
    static CharSource empty() {
        return CharSequenceSource.EMPTY;
    }

    /**
     * Returns a source that reads a character sequence. The sequence is not copied: changes to it, as in a
     * {@link StringBuilder}, show in the reads that follow.
     *
     * @param chars the sequence to read
     * @return a source of {@code chars}
     */
    static CharSource wrap(CharSequence chars) {
        return new CharSequenceSource(chars);
    }

    /**
     * Returns a source that reads a text file.
     *
     * @param file the file to read
     * @param charset the charset the file is encoded in
     * @param options how to open the file, as for {@link java.nio.file.Files#newInputStream(Path, OpenOption...)}
     * @return a source of the content of {@code file}
     * @see ByteSource#asCharSource(Charset)
     */
    static CharSource of(Path file, Charset charset, OpenOption... options) {
        return ByteSource.of(file, options).asCharSource(charset);
    }

    /**
     * Returns a source that reads the text a URL points to, such as a resource from {@link Class#getResource(String)}.
     *
     * @param url the URL to read
     * @param charset the charset the text is encoded in
     * @return a source of the content at {@code url}
     * @see ByteSource#asCharSource(Charset)
     */
    static CharSource of(URL url, Charset charset) {
        return ByteSource.of(url).asCharSource(charset);
    }

    /**
     * Returns a source that reads the text a URI points to, through the URL it converts to.
     *
     * @param uri the URI to read
     * @param charset the charset the text is encoded in
     * @return a source of the content at {@code uri}
     * @throws IllegalArgumentException if {@code uri} is not absolute, or has a scheme no URL protocol handler supports
     * @see ByteSource#asCharSource(Charset)
     */
    static CharSource of(URI uri, Charset charset) {
        return ByteSource.of(uri).asCharSource(charset);
    }

    /**
     * Returns a source that reads several sources one after the other. Each source is opened only when the previous one
     * is read to its end.
     *
     * @param sources the sources to read
     * @return a source of the content of {@code sources}, in order
     */
    static CharSource concat(CharSource... sources) {
        return new ConcatenatedCharSource(Arrays.asList(requireNonNull(sources, "sources")));
    }

    /**
     * Returns a source that reads several sources one after the other. Each source is opened only when the previous one
     * is read to its end. The sources are copied: later changes to {@code sources} have no effect.
     *
     * @param sources the sources to read
     * @return a source of the content of {@code sources}, in order
     */
    static CharSource concat(Iterable<? extends CharSource> sources) {
        return new ConcatenatedCharSource(sources);
    }

    /**
     * Opens a new stream on the characters of this source. The caller must close it.
     *
     * @return a new stream
     * @throws IOException if the stream cannot be opened
     */
    Reader openStream() throws IOException;

    /**
     * Opens a new buffered stream on the characters of this source. The caller must close it.
     *
     * @return a new buffered stream
     * @throws IOException if the stream cannot be opened
     */
    default BufferedReader openBufferedStream() throws IOException {
        var in = openStream();
        return in instanceof BufferedReader buffered ? buffered : new BufferedReader(in);
    }

    /**
     * Tells whether this source has no characters, reading at most one character to find out.
     *
     * @return whether this source is empty
     * @throws IOException if this source cannot be read
     */
    default boolean isEmpty() throws IOException {
        var length = lengthIfKnown();
        if (length.isPresent()) {
            return length.getAsLong() == 0;
        }
        try (var in = openStream()) {
            return in.read() == -1;
        }
    }

    /**
     * Returns the number of characters in this source if it is known without reading them. A source that changes may
     * then read a different number of characters.
     *
     * @return the number of characters, or nothing if it is unknown
     */
    default OptionalLong lengthIfKnown() {
        return OptionalLong.empty();
    }

    /**
     * Returns the number of characters in this source, reading them all if {@link #lengthIfKnown()} does not know it.
     *
     * @return the number of characters
     * @throws IOException if this source cannot be read
     */
    default long length() throws IOException {
        var length = lengthIfKnown();
        if (length.isPresent()) {
            return length.getAsLong();
        }
        try (var in = openStream()) {
            return in.transferTo(Writer.nullWriter());
        }
    }

    /**
     * Reads all the characters of this source.
     *
     * @return the characters of this source
     * @throws IOException if this source cannot be read
     */
    default String read() throws IOException {
        var text = new StringBuilder();
        copyTo(text);
        return text.toString();
    }

    /**
     * Reads the first line of this source.
     *
     * @return the first line, or nothing if this source is empty
     * @throws IOException if this source cannot be read
     */
    default Optional<String> readFirstLine() throws IOException {
        try (var in = openBufferedStream()) {
            return Optional.ofNullable(in.readLine());
        }
    }

    /**
     * Reads all the lines of this source.
     *
     * @return the lines of this source, in an unmodifiable list
     * @throws IOException if this source cannot be read
     */
    default List<String> readLines() throws IOException {
        var lines = new ArrayList<String>();
        forEachLine(lines::add);
        return List.copyOf(lines);
    }

    /**
     * Opens a stream of the lines of this source, read as the stream is consumed. The caller must close the stream,
     * which closes what it reads from. A failure to read during a terminal operation is thrown as an
     * {@link UncheckedIOException}.
     *
     * <pre>{@code
     * try (Stream<String> lines = source.lines()) {
     *     return lines.filter(line -> !line.isBlank()).count();
     * }
     * }</pre>
     *
     * @return a new stream of the lines of this source; it is the caller's responsibility to close it after use
     * @throws IOException if this source cannot be opened
     */
    default Stream<String> lines() throws IOException {
        var in = openBufferedStream();
        return in.lines().onClose(() -> {
            try {
                in.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Reads the lines of this source one at a time, without keeping them.
     *
     * @param action what to do with each line
     * @throws IOException if this source cannot be read
     */
    default void forEachLine(Consumer<? super String> action) throws IOException {
        requireNonNull(action, "action");
        try (var in = openBufferedStream()) {
            for (var line = in.readLine(); line != null; line = in.readLine()) {
                action.accept(line);
            }
        }
    }

    /**
     * Copies the characters of this source to an appendable, which is left open if it is a stream.
     *
     * @param out the appendable to write to
     * @return the number of characters copied
     * @throws IOException if this source cannot be read or {@code out} cannot be written
     */
    default long copyTo(Appendable out) throws IOException {
        requireNonNull(out, "out");
        try (var in = openStream()) {
            return Streams.copy(in, out);
        }
    }

    /**
     * Copies the characters of this source to a sink.
     *
     * @param sink the sink to write to
     * @return the number of characters copied
     * @throws IOException if this source cannot be read or {@code sink} cannot be written
     */
    default long copyTo(CharSink sink) throws IOException {
        requireNonNull(sink, "sink");
        try (var in = openStream();
                var out = sink.openStream()) {
            return in.transferTo(out);
        }
    }

    /**
     * Returns a source of the bytes that encode these characters. Reading it fails with a
     * {@link java.nio.charset.CharacterCodingException} on characters {@code charset} cannot encode, or on a malformed
     * surrogate pair, rather than replace them.
     *
     * @param charset the charset to encode the characters in
     * @return a source of the encoded bytes
     * @throws IllegalArgumentException if {@code charset} does not support encoding
     */
    default ByteSource asByteSource(Charset charset) {
        requireNonNull(charset, "charset");
        checkArgument(charset.canEncode(), "%s does not support encoding", charset);
        return () -> new EncodingInputStream(openStream(), charset.newEncoder());
    }
}
