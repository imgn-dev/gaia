package be.imgn.gaia.core.io;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.checkFromToIndex;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.io.internal.Streams;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.CharBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Writes a stream of characters, with calls that chain.
 *
 * <p>A writer on a {@link Writer} holds a buffer, which it writes to the stream when it is full, on {@link #flush()}
 * and on {@link #close()}. A writer on any other {@link Appendable}, such as a {@link StringBuilder}, writes to it
 * directly.
 *
 * <pre>{@code
 * try (var out = CharSink.of(file).openWriter()) {
 *     out.write("# ").writeLine(title);
 *     for (var entry : entries) {
 *         out.write(entry.key()).write(" = ").writeLine(entry.value());
 *     }
 * }
 * }</pre>
 *
 * <p>A writer owns its stream: {@link #close()} closes it, and any write after that throws {@link IOException}. A
 * writer is not safe for use by several threads at once.
 *
 * @since 0.2.0
 */
public final class CharWriter implements Appendable, Closeable, Flushable {

    private static final char[] NO_CHARS = {};

    private final Appendable out;
    // Null when out isn't a Writer, which is then written to directly.
    private final @Nullable Writer writer;
    // Empty once closed, so that any write that needs room goes through flushBuffer(), which then fails.
    private char[] buffer;
    private int count;
    private boolean closed;

    private CharWriter(Appendable out) {
        this.out = out;
        writer = out instanceof Writer w ? w : null;
        buffer = writer != null ? new char[Streams.BUFFER_SIZE] : NO_CHARS;
    }

    /**
     * Returns a writer that writes to a stream, which it then owns. If {@code out} is the {@linkplain #asWriter()
     * stream view} of a writer, returns that writer rather than buffering its characters again.
     *
     * @param out the stream to write to
     * @return a writer that writes to {@code out}
     */
    public static CharWriter of(Writer out) {
        requireNonNull(out, "out");
        return of((Appendable) out);
    }

    /**
     * Returns a writer that writes to an {@link Appendable}, such as a {@link StringBuilder}, which it then owns: it
     * flushes it if it is {@link Flushable}, and closes it if it is {@link Closeable}. Each write goes to it directly,
     * unless it is a {@link Writer}. If {@code out} is the {@linkplain #asWriter() stream view} of a writer, returns
     * that writer.
     *
     * @param out where to write
     * @return a writer that writes to {@code out}
     */
    public static CharWriter of(Appendable out) {
        requireNonNull(out, "out");
        if (out instanceof View view) {
            return view.owner();
        }
        return new CharWriter(out);
    }

    /**
     * Writes characters.
     *
     * @param chars the characters
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    public CharWriter write(CharSequence chars) throws IOException {
        requireNonNull(chars, "chars");
        writeChars(chars, 0, chars.length());
        return this;
    }

    /**
     * Writes part of a character sequence.
     *
     * @param chars the sequence
     * @param start the index of the first character to write
     * @param end the index after the last character to write
     * @return this writer
     * @throws IndexOutOfBoundsException if {@code start} is negative, greater than {@code end}, or if {@code end} is
     *     greater than the length of {@code chars}
     * @throws IOException if the stream cannot be written
     */
    public CharWriter write(CharSequence chars, int start, int end) throws IOException {
        requireNonNull(chars, "chars");
        checkFromToIndex(start, end, chars.length());
        writeChars(chars, start, end);
        return this;
    }

    /**
     * Writes a {@code char}.
     *
     * @param c the {@code char}
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    public CharWriter write(char c) throws IOException {
        if (writer == null) {
            ensureOpen();
            out.append(c);
            return this;
        }
        if (count == buffer.length) {
            flushBuffer();
        }
        buffer[count] = c;
        count++;
        return this;
    }

    /**
     * Writes a code point: two {@code char}s if it is a supplementary character, one otherwise.
     *
     * @param codePoint the code point
     * @return this writer
     * @throws IllegalArgumentException if {@code codePoint} is not a valid code point
     * @throws IOException if the stream cannot be written
     * @see Character#isValidCodePoint(int)
     */
    public CharWriter writeCodePoint(int codePoint) throws IOException {
        checkArgument(Character.isValidCodePoint(codePoint), "Not a valid code point: %s", codePoint);
        if (Character.isBmpCodePoint(codePoint)) {
            return write((char) codePoint);
        }
        return write(Character.highSurrogate(codePoint)).write(Character.lowSurrogate(codePoint));
    }

    /**
     * Writes the line separator of the system.
     *
     * @return this writer
     * @throws IOException if the stream cannot be written
     * @see System#lineSeparator()
     */
    public CharWriter newLine() throws IOException {
        return write(System.lineSeparator());
    }

    /**
     * Writes characters followed by the line separator of the system.
     *
     * @param line the characters
     * @return this writer
     * @throws IOException if the stream cannot be written
     * @see System#lineSeparator()
     */
    public CharWriter writeLine(CharSequence line) throws IOException {
        return write(line).newLine();
    }

    /**
     * Writes characters, or {@code "null"} for a null sequence, as {@link Appendable#append(CharSequence)} specifies.
     *
     * @param chars the characters, or null
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    @Override
    public CharWriter append(@Nullable CharSequence chars) throws IOException {
        return write(chars == null ? "null" : chars);
    }

    /**
     * Writes part of a character sequence, or of {@code "null"} for a null sequence, as
     * {@link Appendable#append(CharSequence, int, int)} specifies.
     *
     * @param chars the sequence, or null
     * @param start the index of the first character to write
     * @param end the index after the last character to write
     * @return this writer
     * @throws IndexOutOfBoundsException if {@code start} is negative, greater than {@code end}, or if {@code end} is
     *     greater than the length of the sequence
     * @throws IOException if the stream cannot be written
     */
    @Override
    public CharWriter append(@Nullable CharSequence chars, int start, int end) throws IOException {
        return write(chars == null ? "null" : chars, start, end);
    }

    /**
     * Writes a {@code char}, as {@link Appendable#append(char)} specifies.
     *
     * @param c the {@code char}
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    @Override
    public CharWriter append(char c) throws IOException {
        return write(c);
    }

    /**
     * Writes the characters of a stream up to its end. The stream stays open.
     *
     * @param in the stream to read
     * @return the number of characters written
     * @throws IOException if {@code in} cannot be read, or the stream of this writer cannot be written
     */
    public long transferFrom(Reader in) throws IOException {
        requireNonNull(in, "in");
        ensureOpen();
        if (writer == null) {
            return Streams.copy(in, out);
        }
        var total = 0L;
        while (true) {
            if (count == buffer.length) {
                flushBuffer();
            }
            var read = in.read(buffer, count, buffer.length - count);
            if (read < 0) {
                return total;
            }
            count += read;
            total += read;
        }
    }

    /**
     * Writes the buffered characters to the stream, and flushes it if it is {@link Flushable}.
     *
     * @throws IOException if the stream cannot be written
     */
    @Override
    public void flush() throws IOException {
        flushBuffer();
        if (out instanceof Flushable flushable) {
            flushable.flush();
        }
    }

    /**
     * Returns a stream that writes through this writer, for APIs that take a {@link Writer}. Writes on the stream and
     * on this writer go through the same buffer, in the order they are made. Closing the stream closes this writer.
     *
     * @return a stream that writes through this writer
     */
    public Writer asWriter() {
        return new View();
    }

    /**
     * Writes the buffered characters to the stream, then closes it if it is {@link Closeable}, even if writing fails,
     * or flushes it if it is only {@link Flushable}. Closing a writer again has no effect.
     *
     * @throws IOException if the stream cannot be written or closed; if both fail, the second exception is suppressed
     *     by the first
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        var closeable = out instanceof Closeable c ? c : null;
        try (closeable) {
            flushBuffer();
            if (closeable == null && out instanceof Flushable flushable) {
                flushable.flush();
            }
        } finally {
            closed = true;
            buffer = NO_CHARS;
            count = 0;
        }
    }

    private void writeChars(CharSequence chars, int start, int end) throws IOException {
        if (writer == null) {
            ensureOpen();
            out.append(chars, start, end);
            return;
        }
        var length = end - start;
        if (length > buffer.length - count) {
            flushBuffer();
            if (length >= buffer.length) {
                if (chars instanceof String string) {
                    writer.write(string, start, length);
                } else {
                    writer.append(chars, start, end);
                }
                return;
            }
        }
        if (chars instanceof String string) {
            string.getChars(start, end, buffer, count);
        } else {
            for (int i = start; i < end; i++) {
                buffer[count + i - start] = chars.charAt(i);
            }
        }
        count += length;
    }

    private void writeChars(char[] chars, int offset, int length) throws IOException {
        if (writer == null) {
            ensureOpen();
            out.append(CharBuffer.wrap(chars, offset, length));
            return;
        }
        if (length > buffer.length - count) {
            flushBuffer();
            if (length >= buffer.length) {
                writer.write(chars, offset, length);
                return;
            }
        }
        System.arraycopy(chars, offset, buffer, count, length);
        count += length;
    }

    private void flushBuffer() throws IOException {
        ensureOpen();
        if (writer != null && count > 0) {
            writer.write(buffer, 0, count);
            count = 0;
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Writer closed");
        }
    }

    private final class View extends Writer {

        CharWriter owner() {
            return CharWriter.this;
        }

        @Override
        public void write(int c) throws IOException {
            CharWriter.this.write((char) c);
        }

        @Override
        public void write(char[] chars, int offset, int length) throws IOException {
            checkFromIndexSize(offset, length, chars.length);
            writeChars(chars, offset, length);
        }

        @Override
        public void write(String string, int offset, int length) throws IOException {
            checkFromIndexSize(offset, length, string.length());
            writeChars(string, offset, offset + length);
        }

        @Override
        public void flush() throws IOException {
            CharWriter.this.flush();
        }

        @Override
        public void close() throws IOException {
            CharWriter.this.close();
        }
    }
}
