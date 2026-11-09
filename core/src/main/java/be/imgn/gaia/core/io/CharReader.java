package be.imgn.gaia.core.io;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.io.internal.Streams;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.CharBuffer;
import java.util.Optional;
import java.util.function.IntPredicate;

/**
 * Reads a stream of characters: lines, runs of characters, lookahead and search, for reading text formats.
 *
 * <p>A reader holds a buffer, and reads from its stream only when a call needs characters that aren't buffered yet,
 * taking what the stream has at hand: it never waits for more characters than the call needs.
 *
 * <p>Counts and offsets are in {@code char}s. The methods that test characters with a predicate give it code points,
 * joining surrogate pairs, so that the methods of {@link Character} that take an {@code int}, such as
 * {@link Character#isWhitespace(int)}, work on every character.
 *
 * <p>When the stream ends before a read method has all the characters it needs, the method throws {@link EOFException}
 * and leaves the reader at the end of the stream. The lookahead methods, {@link #request(int)}, {@link #require(int)},
 * {@link #exhausted()}, {@link #peek()}, {@link #startsWith(CharSequence)} and the {@code indexOf} methods, never
 * consume anything.
 *
 * <pre>{@code
 * // key = value lines, with # comments
 * while (!in.exhausted()) {
 *     in.skipWhile(Character::isWhitespace);
 *     if (in.startsWith("#")) {
 *         in.readLine(MAX_LINE);
 *         continue;
 *     }
 *     var key = in.readWhile(c -> c != '=' && !Character.isWhitespace(c), MAX_KEY);
 *     in.skipWhile(c -> c == ' ' || c == '\t');
 *     if (in.peek() != '=') {
 *         throw new IOException("expected '=' after " + key);
 *     }
 *     in.skip(1);
 *     var value = in.readLine(MAX_LINE).orElse("").strip();
 * }
 * }</pre>
 *
 * <p>A reader owns its stream: {@link #close()} closes it, and any other call that needs the stream after that throws
 * {@link IOException}. A reader is not safe for use by several threads at once.
 *
 * @since 0.2.0
 */
public final class CharReader implements Closeable {

    private static final char[] NO_CHARS = {};

    // The largest array that every JVM can allocate.
    private static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    private final Reader in;
    private char[] buffer = new char[Streams.BUFFER_SIZE];
    // The buffered characters are those from position, included, to limit, excluded.
    private int position;
    private int limit;
    private boolean closed;

    private CharReader(Reader in) {
        this.in = in;
    }

    /**
     * Returns a reader that reads a stream, which it then owns. If {@code in} is the {@linkplain #asReader() stream
     * view} of a reader, returns that reader rather than buffering its characters again.
     *
     * @param in the stream to read
     * @return a reader that reads {@code in}
     */
    public static CharReader of(Reader in) {
        requireNonNull(in, "in");
        if (in instanceof View view) {
            return view.owner();
        }
        return new CharReader(in);
    }

    /**
     * Returns a reader that reads characters.
     *
     * @param chars the characters to read
     * @return a reader that reads {@code chars}
     */
    public static CharReader of(CharSequence chars) {
        requireNonNull(chars, "chars");
        return new CharReader(new StringReader(chars.toString()));
    }

    /**
     * Buffers characters ahead, without consuming them.
     *
     * @param charCount the number of characters to buffer
     * @return whether {@code charCount} characters are buffered; false if the stream ends first
     * @throws IllegalArgumentException if {@code charCount} is negative
     * @throws IOException if the stream cannot be read
     */
    public boolean request(int charCount) throws IOException {
        checkNotNegative("charCount", charCount);
        return fill(charCount);
    }

    /**
     * Buffers characters ahead, without consuming them, or fails if the stream ends first.
     *
     * @param charCount the number of characters to buffer
     * @throws IllegalArgumentException if {@code charCount} is negative
     * @throws EOFException if the stream ends before {@code charCount} characters
     * @throws IOException if the stream cannot be read
     */
    public void require(int charCount) throws IOException {
        if (!request(charCount)) {
            throw new EOFException();
        }
    }

    /**
     * Tells whether the stream has no more characters, reading one ahead if none is buffered.
     *
     * @return whether the reader is at the end of the stream
     * @throws IOException if the stream cannot be read
     */
    public boolean exhausted() throws IOException {
        return !fill(1);
    }

    /**
     * Returns the next character, without consuming it.
     *
     * @return the next {@code char}, or -1 at the end of the stream
     * @throws IOException if the stream cannot be read
     */
    public int peek() throws IOException {
        return fill(1) ? buffer[position] : -1;
    }

    /**
     * Tells whether the next characters are some others, without consuming them.
     *
     * @param prefix the characters to look for
     * @return whether the next characters are those of {@code prefix}
     * @throws IOException if the stream cannot be read
     */
    public boolean startsWith(CharSequence prefix) throws IOException {
        requireNonNull(prefix, "prefix");
        return fill(prefix.length()) && regionEquals(0, prefix);
    }

    /**
     * Returns the offset of the first occurrence of a character among the next ones, without consuming them.
     *
     * @param c the character to look for
     * @param maxChars the number of characters to look at, at most
     * @return the offset of the first {@code c} from the next character, or -1 if none of the next {@code maxChars}
     *     characters before the end of the stream is {@code c}
     * @throws IllegalArgumentException if {@code maxChars} is negative
     * @throws IOException if the stream cannot be read
     */
    public int indexOf(char c, int maxChars) throws IOException {
        checkNotNegative("maxChars", maxChars);
        for (int i = 0; i < maxChars; i++) {
            if (position + i >= limit && !fill(i + 1)) {
                return -1;
            }
            if (buffer[position + i] == c) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the offset of the first occurrence of characters among the next ones, without consuming them.
     *
     * @param target the characters to look for
     * @param maxChars the number of characters to look at, at most, {@code target} included
     * @return the offset where the first occurrence of {@code target} starts from the next character, 0 if
     *     {@code target} is empty, or -1 if it doesn't occur within the next {@code maxChars} characters before the end
     *     of the stream
     * @throws IllegalArgumentException if {@code maxChars} is negative
     * @throws IOException if the stream cannot be read
     */
    public int indexOf(CharSequence target, int maxChars) throws IOException {
        requireNonNull(target, "target");
        checkNotNegative("maxChars", maxChars);
        var length = target.length();
        for (int i = 0; i <= maxChars - length; i++) {
            if (!fill(i + length)) {
                return -1;
            }
            if (regionEquals(i, target)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Reads a {@code char}.
     *
     * @return the {@code char}
     * @throws EOFException if the stream has no more characters
     * @throws IOException if the stream cannot be read
     */
    public char readChar() throws IOException {
        var index = take(1);
        return buffer[index];
    }

    /**
     * Reads a code point: two {@code char}s if they are a surrogate pair, one otherwise. An unpaired surrogate is
     * returned as it is.
     *
     * @return the code point
     * @throws EOFException if the stream has no more characters
     * @throws IOException if the stream cannot be read
     */
    public int readCodePoint() throws IOException {
        var c = readChar();
        if (Character.isHighSurrogate(c) && fill(1) && Character.isLowSurrogate(buffer[position])) {
            return Character.toCodePoint(c, buffer[position++]);
        }
        return c;
    }

    /**
     * Reads characters.
     *
     * @param charCount the number of characters to read
     * @return the next {@code charCount} characters
     * @throws IllegalArgumentException if {@code charCount} is negative
     * @throws EOFException if the stream ends before {@code charCount} characters
     * @throws IOException if the stream cannot be read
     */
    public String readString(int charCount) throws IOException {
        checkNotNegative("charCount", charCount);
        if (charCount <= buffer.length) {
            var index = take(charCount);
            return new String(buffer, index, charCount);
        }
        // A large count, maybe read from the stream itself, isn't allocated before the characters have been read.
        var builder = new StringBuilder();
        while (builder.length() < charCount) {
            if (!fill(1)) {
                throw new EOFException();
            }
            var count = Math.min(charCount - builder.length(), limit - position);
            builder.append(buffer, position, count);
            position += count;
        }
        return builder.toString();
    }

    /**
     * Reads the characters up to the end of the stream.
     *
     * @return the rest of the characters
     * @throws IOException if the stream cannot be read
     */
    public String readAll() throws IOException {
        ensureOpen();
        var builder = new StringBuilder();
        while (fill(1)) {
            builder.append(buffer, position, limit - position);
            position = limit;
        }
        return builder.toString();
    }

    /**
     * Reads a line. A line ends with a line feed ({@code \n}), a carriage return ({@code \r}), a carriage return
     * followed by a line feed, or the end of the stream, as for {@link java.io.BufferedReader#readLine()}.
     *
     * @param maxLength the number of characters the line may have, at most, its terminator excluded
     * @return the line, without its terminator, or an empty optional at the end of the stream
     * @throws IllegalArgumentException if {@code maxLength} is negative
     * @throws IOException if the line has more than {@code maxLength} characters, in which case nothing is consumed, or
     *     if the stream cannot be read
     */
    public Optional<String> readLine(int maxLength) throws IOException {
        checkNotNegative("maxLength", maxLength);
        // Scans the buffered characters in a tight loop, and fills the buffer only once they are all scanned.
        var scanned = 0;
        while (true) {
            var end = (int) Math.min(limit, (long) position + maxLength + 1);
            for (int i = position + scanned; i < end; i++) {
                var c = buffer[i];
                if (c == '\n' || c == '\r') {
                    return Optional.of(consumeLine(i - position, c));
                }
            }
            scanned = end - position;
            if (scanned > maxLength) {
                throw new IOException("Line longer than " + maxLength + " characters");
            }
            if (!fill(scanned + 1)) {
                return scanned == 0 ? Optional.empty() : Optional.of(consumeLine(scanned, -1));
            }
        }
    }

    /**
     * Reads the code points that pass a test, up to the first that doesn't, a length limit or the end of the stream.
     * Reaching the limit is not an error: {@link #peek()} or another lookahead tells whether the run goes on.
     *
     * @param test the test that the code points to read pass
     * @param maxLength the number of characters to read, at most; a surrogate pair that would exceed it is not read
     * @return the characters read, which may be none
     * @throws IllegalArgumentException if {@code maxLength} is negative
     * @throws IOException if the stream cannot be read
     */
    public String readWhile(IntPredicate test, int maxLength) throws IOException {
        requireNonNull(test, "test");
        checkNotNegative("maxLength", maxLength);
        var length = 0;
        while (position + length < limit || fill(length + 1)) {
            var width = codePointWidth(length);
            if (length + width > maxLength || !test.test(codePointAt(length, width))) {
                break;
            }
            length += width;
        }
        var string = new String(buffer, position, length);
        position += length;
        return string;
    }

    /**
     * Skips the code points that pass a test, up to the first that doesn't or the end of the stream.
     *
     * @param test the test that the code points to skip pass
     * @return the number of characters skipped
     * @throws IOException if the stream cannot be read
     */
    public long skipWhile(IntPredicate test) throws IOException {
        requireNonNull(test, "test");
        var skipped = 0L;
        while (fill(1)) {
            var width = codePointWidth(0);
            if (!test.test(codePointAt(0, width))) {
                break;
            }
            position += width;
            skipped += width;
        }
        return skipped;
    }

    /**
     * Skips characters.
     *
     * @param charCount the number of characters to skip
     * @throws IllegalArgumentException if {@code charCount} is negative
     * @throws EOFException if the stream ends before {@code charCount} characters
     * @throws IOException if the stream cannot be read
     */
    public void skip(long charCount) throws IOException {
        checkNotNegative("charCount", charCount);
        var remaining = charCount;
        while (remaining > 0) {
            if (!fill(1)) {
                throw new EOFException();
            }
            var count = (int) Math.min(remaining, limit - position);
            position += count;
            remaining -= count;
        }
    }

    /**
     * Writes the characters up to the end of the stream to an {@link Appendable}, such as a {@link Writer} or a
     * {@link StringBuilder}, which stays open.
     *
     * @param out where to write the characters
     * @return the number of characters written
     * @throws IOException if the stream cannot be read, or {@code out} cannot be written
     */
    public long transferTo(Appendable out) throws IOException {
        requireNonNull(out, "out");
        ensureOpen();
        var total = 0L;
        while (fill(1)) {
            var count = limit - position;
            if (out instanceof Writer writer) {
                writer.write(buffer, position, count);
            } else {
                out.append(CharBuffer.wrap(buffer, position, count));
            }
            position = limit;
            total += count;
        }
        return total;
    }

    /**
     * Returns a stream that reads the rest of the characters through this reader, for APIs that take a {@link Reader}.
     * Reads on the stream and on this reader take turns without skipping or repeating a character. Closing the stream
     * closes this reader.
     *
     * @return a stream on the rest of the characters
     */
    public Reader asReader() {
        return new View();
    }

    /**
     * Closes the stream. Closing a reader again has no effect.
     *
     * @throws IOException if the stream cannot be closed
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        buffer = NO_CHARS;
        position = 0;
        limit = 0;
        in.close();
    }

    // Buffers at least charCount characters, reading no more than the stream has at hand; false if it ends first.
    private boolean fill(int charCount) throws IOException {
        if (limit - position >= charCount) {
            return true;
        }
        ensureOpen();
        if (position == limit) {
            position = 0;
            limit = 0;
        }
        if (buffer.length - position < charCount) {
            var buffered = limit - position;
            var target = charCount <= buffer.length
                    ? buffer
                    : new char[Math.max(charCount, (int) Math.min(2L * buffer.length, MAX_ARRAY_LENGTH))];
            System.arraycopy(buffer, position, target, 0, buffered);
            buffer = target;
            position = 0;
            limit = buffered;
        }
        while (limit - position < charCount) {
            var read = in.read(buffer, limit, buffer.length - limit);
            if (read < 0) {
                return false;
            }
            limit += read;
        }
        return true;
    }

    // Consumes a line and its terminator, -1 at the end of the stream, and returns the line.
    private String consumeLine(int length, int terminator) throws IOException {
        var line = new String(buffer, position, length);
        position += length;
        if (terminator != -1) {
            position++;
            if (terminator == '\r' && fill(1) && buffer[position] == '\n') {
                position++;
            }
        }
        return line;
    }

    // Consumes charCount characters and returns the index of the first in the buffer, which it may replace: callers
    // read the buffer field after calling it.
    private int take(int charCount) throws IOException {
        if (!fill(charCount)) {
            position = limit;
            throw new EOFException();
        }
        var index = position;
        position += charCount;
        return index;
    }

    // The number of chars of the code point at an offset, whose first char is buffered: 2 for a surrogate pair.
    private int codePointWidth(int offset) throws IOException {
        return Character.isHighSurrogate(buffer[position + offset])
                        && fill(offset + 2)
                        && Character.isLowSurrogate(buffer[position + offset + 1])
                ? 2
                : 1;
    }

    private int codePointAt(int offset, int width) {
        var c = buffer[position + offset];
        return width == 2 ? Character.toCodePoint(c, buffer[position + offset + 1]) : c;
    }

    // Whether the buffered characters at an offset are those of other, which must all be buffered.
    private boolean regionEquals(int offset, CharSequence other) {
        for (int i = 0; i < other.length(); i++) {
            if (buffer[position + offset + i] != other.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Reader closed");
        }
    }

    private static void checkNotNegative(String name, long value) {
        checkArgument(value >= 0, "%s must not be negative: %s", name, value);
    }

    private final class View extends Reader {

        CharReader owner() {
            return CharReader.this;
        }

        @Override
        public int read() throws IOException {
            return fill(1) ? buffer[position++] : -1;
        }

        @Override
        public int read(char[] b, int offset, int length) throws IOException {
            checkFromIndexSize(offset, length, b.length);
            if (length == 0) {
                return 0;
            }
            if (!fill(1)) {
                return -1;
            }
            var count = Math.min(length, limit - position);
            System.arraycopy(buffer, position, b, offset, count);
            position += count;
            return count;
        }

        @Override
        public long skip(long n) throws IOException {
            checkArgument(n >= 0, "skip value is negative");
            if (n == 0 || !fill(1)) {
                return 0;
            }
            var skipped = (int) Math.min(n, limit - position);
            position += skipped;
            return skipped;
        }

        @Override
        public boolean ready() throws IOException {
            ensureOpen();
            return position < limit || in.ready();
        }

        @Override
        public long transferTo(Writer out) throws IOException {
            return CharReader.this.transferTo(out);
        }

        @Override
        public void close() throws IOException {
            CharReader.this.close();
        }
    }
}
