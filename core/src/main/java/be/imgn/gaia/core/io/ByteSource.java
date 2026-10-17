package be.imgn.gaia.core.io;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.io.internal.ByteArraySource;
import be.imgn.gaia.core.io.internal.ConcatenatedByteSource;
import be.imgn.gaia.core.io.internal.PathSource;
import be.imgn.gaia.core.io.internal.SlicedByteSource;
import be.imgn.gaia.core.io.internal.Streams;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.OptionalLong;

/**
 * A readable source of bytes, such as a file or a URL, that opens a new stream each time it is read.
 *
 * <p>Unlike an {@link InputStream}, a source is not an open resource: it can be kept, passed around and read any number
 * of times, and each read method opens a stream and closes it before returning. Only {@link #openStream()} and
 * {@link #openBufferedStream()} leave a stream for the caller to close.
 *
 * <p>The only abstract method is {@link #openStream()}, so a lambda makes a source:
 *
 * <pre>{@code
 * ByteSource source = () -> socket.getInputStream();
 * byte[] bytes = ByteSource.of(file).read();
 * String text = ByteSource.of(uri).asCharSource(StandardCharsets.UTF_8).read();
 * }</pre>
 *
 * <p>Implementations may override the other methods with faster ones, such as {@link #sizeIfKnown()} for a source that
 * knows its size without reading it.
 *
 * @since 0.1.1
 */
@FunctionalInterface
public interface ByteSource {

    /**
     * Returns a source with no bytes.
     *
     * @return an empty source
     */
    static ByteSource empty() {
        return ByteArraySource.EMPTY;
    }

    /**
     * Returns a source that reads an array. The array is not copied: changes to it show in the reads that follow.
     *
     * @param bytes the array to read
     * @return a source of {@code bytes}
     */
    static ByteSource wrap(byte[] bytes) {
        return new ByteArraySource(bytes);
    }

    /**
     * Returns a source that reads a file.
     *
     * @param file the file to read
     * @param options how to open the file, as for {@link java.nio.file.Files#newInputStream(Path, OpenOption...)}
     * @return a source of the content of {@code file}
     */
    static ByteSource of(Path file, OpenOption... options) {
        return new PathSource(file, options);
    }

    /**
     * Returns a source that reads what a URL points to, such as a resource from {@link Class#getResource(String)}.
     *
     * @param url the URL to read
     * @return a source of the content at {@code url}
     */
    static ByteSource of(URL url) {
        requireNonNull(url, "url");
        return url::openStream;
    }

    /**
     * Returns a source that reads what a URI points to, through the URL it converts to.
     *
     * @param uri the URI to read
     * @return a source of the content at {@code uri}
     * @throws IllegalArgumentException if {@code uri} is not absolute, or has a scheme no URL protocol handler supports
     */
    static ByteSource of(URI uri) {
        requireNonNull(uri, "uri");
        try {
            return of(uri.toURL());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("No URL protocol handler for " + uri, e);
        }
    }

    /**
     * Returns a source that reads several sources one after the other. Each source is opened only when the previous one
     * is read to its end.
     *
     * @param sources the sources to read
     * @return a source of the content of {@code sources}, in order
     */
    static ByteSource concat(ByteSource... sources) {
        return new ConcatenatedByteSource(Arrays.asList(requireNonNull(sources, "sources")));
    }

    /**
     * Returns a source that reads several sources one after the other. Each source is opened only when the previous one
     * is read to its end. The sources are copied: later changes to {@code sources} have no effect.
     *
     * @param sources the sources to read
     * @return a source of the content of {@code sources}, in order
     */
    static ByteSource concat(Iterable<? extends ByteSource> sources) {
        return new ConcatenatedByteSource(sources);
    }

    /**
     * Opens a new stream on the bytes of this source. The caller must close it.
     *
     * @return a new stream
     * @throws IOException if the stream cannot be opened
     */
    InputStream openStream() throws IOException;

    /**
     * Opens a new buffered stream on the bytes of this source. The caller must close it.
     *
     * @return a new buffered stream
     * @throws IOException if the stream cannot be opened
     */
    default InputStream openBufferedStream() throws IOException {
        var in = openStream();
        return in instanceof BufferedInputStream ? in : new BufferedInputStream(in);
    }

    /**
     * Tells whether this source has no bytes, reading at most one byte to find out.
     *
     * @return whether this source is empty
     * @throws IOException if this source cannot be read
     */
    default boolean isEmpty() throws IOException {
        var size = sizeIfKnown();
        if (size.isPresent()) {
            return size.getAsLong() == 0;
        }
        try (var in = openStream()) {
            return in.read() == -1;
        }
    }

    /**
     * Returns the number of bytes in this source if it is known without reading them. A source that changes may then
     * read a different number of bytes.
     *
     * @return the number of bytes, or nothing if it is unknown
     */
    default OptionalLong sizeIfKnown() {
        return OptionalLong.empty();
    }

    /**
     * Returns the number of bytes in this source, reading them all if {@link #sizeIfKnown()} does not know it.
     *
     * @return the number of bytes
     * @throws IOException if this source cannot be read
     */
    default long size() throws IOException {
        var size = sizeIfKnown();
        if (size.isPresent()) {
            return size.getAsLong();
        }
        try (var in = openStream()) {
            return in.transferTo(OutputStream.nullOutputStream());
        }
    }

    /**
     * Reads all the bytes of this source.
     *
     * @return the bytes of this source, in a new array
     * @throws IOException if this source cannot be read
     */
    default byte[] read() throws IOException {
        try (var in = openStream()) {
            return in.readAllBytes();
        }
    }

    /**
     * Copies the bytes of this source to a stream, which is left open.
     *
     * @param out the stream to write to
     * @return the number of bytes copied
     * @throws IOException if this source cannot be read or {@code out} cannot be written
     */
    default long copyTo(OutputStream out) throws IOException {
        requireNonNull(out, "out");
        try (var in = openStream()) {
            return in.transferTo(out);
        }
    }

    /**
     * Copies the bytes of this source to a sink.
     *
     * @param sink the sink to write to
     * @return the number of bytes copied
     * @throws IOException if this source cannot be read or {@code sink} cannot be written
     */
    default long copyTo(ByteSink sink) throws IOException {
        requireNonNull(sink, "sink");
        try (var in = openStream();
                var out = sink.openStream()) {
            return in.transferTo(out);
        }
    }

    /**
     * Tells whether this source has the same bytes as another one.
     *
     * @param other the source to compare with
     * @return whether both sources have the same bytes
     * @throws IOException if either source cannot be read
     */
    default boolean contentEquals(ByteSource other) throws IOException {
        requireNonNull(other, "other");
        var size = sizeIfKnown();
        var otherSize = other.sizeIfKnown();
        if (size.isPresent() && otherSize.isPresent() && size.getAsLong() != otherSize.getAsLong()) {
            return false;
        }
        try (var in = openStream();
                var otherIn = other.openStream()) {
            return Streams.contentEquals(in, otherIn);
        }
    }

    /**
     * Returns a source that reads a part of this one: at most {@code length} bytes, from {@code offset} on. A slice
     * that starts past the end of this source is empty.
     *
     * @param offset the number of bytes to skip
     * @param length the maximum number of bytes to read
     * @return a source of the bytes in that part
     * @throws IllegalArgumentException if {@code offset} or {@code length} is negative
     */
    default ByteSource slice(long offset, long length) {
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException(
                    "The offset and length must not be negative: offset " + offset + ", length " + length);
        }
        return new SlicedByteSource(this, offset, length);
    }

    /**
     * Returns a source of the characters these bytes encode. Reading it fails with a
     * {@link java.nio.charset.CharacterCodingException} on bytes that are not valid in {@code charset}, rather than
     * replace them.
     *
     * @param charset the charset the bytes are encoded in
     * @return a source of the decoded characters
     */
    default CharSource asCharSource(Charset charset) {
        requireNonNull(charset, "charset");
        return () -> new InputStreamReader(openStream(), charset.newDecoder());
    }
}
