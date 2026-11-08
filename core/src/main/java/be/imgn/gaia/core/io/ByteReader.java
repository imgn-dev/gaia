package be.imgn.gaia.core.io;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.base.Charsets;
import be.imgn.gaia.core.io.internal.Streams;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Reads a stream of bytes: numbers in either byte order, runs of bytes, text and lines, with lookahead and search.
 *
 * <p>A reader holds a buffer, and reads from its stream only when a call needs bytes that aren't buffered yet, taking
 * what the stream has at hand: it never waits for more bytes than the call needs, so it works on sockets and pipes.
 *
 * <p>Methods that read a number read it in big-endian order, as {@link java.io.DataInputStream} does; those whose name
 * ends in {@code Le} read it in little-endian order.
 *
 * <p>When the stream ends before a read method has all the bytes it needs, the method throws {@link EOFException} and
 * leaves the reader at the end of the stream. The lookahead methods, {@link #request(int)}, {@link #require(int)},
 * {@link #exhausted()}, {@link #peek()}, {@link #startsWith(Bytes)} and the {@code indexOf} methods, never consume
 * anything.
 *
 * <pre>{@code
 * try (var in = ByteSource.of(classFile).openReader()) {
 *     if (in.readInt() != 0xCAFEBABE) {
 *         throw new IOException("not a class file: " + classFile);
 *     }
 *     var minor = in.readUnsignedShort();
 *     var major = in.readUnsignedShort();
 * }
 * }</pre>
 *
 * <p>A reader owns its stream: {@link #close()} closes it, and any other call that needs the stream after that throws
 * {@link IOException}. A reader is not safe for use by several threads at once.
 *
 * @since 0.1.3
 */
public final class ByteReader implements Closeable {

    private static final VarHandle SHORT = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle SHORT_LE =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle INT_LE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle LONG_LE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private static final byte[] NO_BYTES = {};
    private static final byte[] CRLF = {'\r', '\n'};

    // The largest array that every JVM can allocate.
    private static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    // Null when the reader reads Bytes, whose array is then the buffer, which must not be written to.
    private final @Nullable InputStream in;
    private byte[] buffer;
    // The buffered bytes are those from position, included, to limit, excluded.
    private int position;
    private int limit;
    private boolean closed;

    private ByteReader(@Nullable InputStream in, byte[] buffer, int position, int limit) {
        this.in = in;
        this.buffer = buffer;
        this.position = position;
        this.limit = limit;
    }

    /**
     * Returns a reader that reads a stream, which it then owns.
     *
     * @param in the stream to read
     * @return a reader that reads {@code in}
     */
    public static ByteReader of(InputStream in) {
        requireNonNull(in, "in");
        return new ByteReader(in, new byte[Streams.BUFFER_SIZE], 0, 0);
    }

    /**
     * Returns a reader that reads bytes, without copying them.
     *
     * @param bytes the bytes to read
     * @return a reader that reads {@code bytes}
     */
    public static ByteReader of(Bytes bytes) {
        requireNonNull(bytes, "bytes");
        return new ByteReader(null, bytes.array(), bytes.offset(), bytes.offset() + bytes.size());
    }

    /**
     * Buffers bytes ahead, without consuming them.
     *
     * @param byteCount the number of bytes to buffer
     * @return whether {@code byteCount} bytes are buffered; false if the stream ends first
     * @throws IllegalArgumentException if {@code byteCount} is negative
     * @throws IOException if the stream cannot be read
     */
    public boolean request(int byteCount) throws IOException {
        checkNotNegative("byteCount", byteCount);
        return fill(byteCount);
    }

    /**
     * Buffers bytes ahead, without consuming them, or fails if the stream ends first.
     *
     * @param byteCount the number of bytes to buffer
     * @throws IllegalArgumentException if {@code byteCount} is negative
     * @throws EOFException if the stream ends before {@code byteCount} bytes
     * @throws IOException if the stream cannot be read
     */
    public void require(int byteCount) throws IOException {
        if (!request(byteCount)) {
            throw new EOFException();
        }
    }

    /**
     * Tells whether the stream has no more bytes, reading one ahead if none is buffered.
     *
     * @return whether the reader is at the end of the stream
     * @throws IOException if the stream cannot be read
     */
    public boolean exhausted() throws IOException {
        return !fill(1);
    }

    /**
     * Returns the next byte, without consuming it.
     *
     * @return the next byte, as an unsigned value from 0 to 255, or -1 at the end of the stream
     * @throws IOException if the stream cannot be read
     */
    public int peek() throws IOException {
        return fill(1) ? Byte.toUnsignedInt(buffer[position]) : -1;
    }

    /**
     * Tells whether the next bytes are some others, without consuming them.
     *
     * @param prefix the bytes to look for
     * @return whether the next bytes are those of {@code prefix}
     * @throws IOException if the stream cannot be read
     */
    public boolean startsWith(Bytes prefix) throws IOException {
        requireNonNull(prefix, "prefix");
        var size = prefix.size();
        return fill(size) && regionEquals(0, prefix);
    }

    /**
     * Returns the offset of the first occurrence of a byte among the next ones, without consuming them.
     *
     * @param b the byte to look for
     * @param maxBytes the number of bytes to look at, at most
     * @return the offset of the first {@code b} from the next byte, or -1 if none of the next {@code maxBytes} bytes
     *     before the end of the stream is {@code b}
     * @throws IllegalArgumentException if {@code maxBytes} is negative
     * @throws IOException if the stream cannot be read
     */
    public int indexOf(byte b, int maxBytes) throws IOException {
        checkNotNegative("maxBytes", maxBytes);
        for (int i = 0; i < maxBytes; i++) {
            if (position + i >= limit && !fill(i + 1)) {
                return -1;
            }
            if (buffer[position + i] == b) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the offset of the first occurrence of bytes among the next ones, without consuming them.
     *
     * @param target the bytes to look for
     * @param maxBytes the number of bytes to look at, at most, {@code target} included
     * @return the offset where the first occurrence of {@code target} starts from the next byte, 0 if {@code target} is
     *     empty, or -1 if it doesn't occur within the next {@code maxBytes} bytes before the end of the stream
     * @throws IllegalArgumentException if {@code maxBytes} is negative
     * @throws IOException if the stream cannot be read
     */
    public int indexOf(Bytes target, int maxBytes) throws IOException {
        requireNonNull(target, "target");
        checkNotNegative("maxBytes", maxBytes);
        var size = target.size();
        for (int i = 0; i <= maxBytes - size; i++) {
            if (!fill(i + size)) {
                return -1;
            }
            if (regionEquals(i, target)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Reads a byte.
     *
     * @return the byte
     * @throws EOFException if the stream has no more bytes
     * @throws IOException if the stream cannot be read
     */
    public byte readByte() throws IOException {
        var index = take(Byte.BYTES);
        return buffer[index];
    }

    /**
     * Reads a byte as an unsigned value.
     *
     * @return the byte, from 0 to 255
     * @throws EOFException if the stream has no more bytes
     * @throws IOException if the stream cannot be read
     */
    public int readUnsignedByte() throws IOException {
        return Byte.toUnsignedInt(readByte());
    }

    /**
     * Reads a big-endian {@code short}: two bytes, the most significant first.
     *
     * @return the {@code short}
     * @throws EOFException if the stream ends before two bytes
     * @throws IOException if the stream cannot be read
     */
    public short readShort() throws IOException {
        var index = take(Short.BYTES);
        return (short) SHORT.get(buffer, index);
    }

    /**
     * Reads a little-endian {@code short}: two bytes, the least significant first.
     *
     * @return the {@code short}
     * @throws EOFException if the stream ends before two bytes
     * @throws IOException if the stream cannot be read
     */
    public short readShortLe() throws IOException {
        var index = take(Short.BYTES);
        return (short) SHORT_LE.get(buffer, index);
    }

    /**
     * Reads a big-endian unsigned 16-bit value.
     *
     * @return the value, from 0 to 65,535
     * @throws EOFException if the stream ends before two bytes
     * @throws IOException if the stream cannot be read
     */
    public int readUnsignedShort() throws IOException {
        return Short.toUnsignedInt(readShort());
    }

    /**
     * Reads a little-endian unsigned 16-bit value.
     *
     * @return the value, from 0 to 65,535
     * @throws EOFException if the stream ends before two bytes
     * @throws IOException if the stream cannot be read
     */
    public int readUnsignedShortLe() throws IOException {
        return Short.toUnsignedInt(readShortLe());
    }

    /**
     * Reads a big-endian {@code int}: four bytes, the most significant first.
     *
     * @return the {@code int}
     * @throws EOFException if the stream ends before four bytes
     * @throws IOException if the stream cannot be read
     */
    public int readInt() throws IOException {
        var index = take(Integer.BYTES);
        return (int) INT.get(buffer, index);
    }

    /**
     * Reads a little-endian {@code int}: four bytes, the least significant first.
     *
     * @return the {@code int}
     * @throws EOFException if the stream ends before four bytes
     * @throws IOException if the stream cannot be read
     */
    public int readIntLe() throws IOException {
        var index = take(Integer.BYTES);
        return (int) INT_LE.get(buffer, index);
    }

    /**
     * Reads a big-endian unsigned 32-bit value.
     *
     * @return the value, from 0 to 4,294,967,295
     * @throws EOFException if the stream ends before four bytes
     * @throws IOException if the stream cannot be read
     */
    public long readUnsignedInt() throws IOException {
        return Integer.toUnsignedLong(readInt());
    }

    /**
     * Reads a little-endian unsigned 32-bit value.
     *
     * @return the value, from 0 to 4,294,967,295
     * @throws EOFException if the stream ends before four bytes
     * @throws IOException if the stream cannot be read
     */
    public long readUnsignedIntLe() throws IOException {
        return Integer.toUnsignedLong(readIntLe());
    }

    /**
     * Reads a big-endian {@code long}: eight bytes, the most significant first.
     *
     * @return the {@code long}
     * @throws EOFException if the stream ends before eight bytes
     * @throws IOException if the stream cannot be read
     */
    public long readLong() throws IOException {
        var index = take(Long.BYTES);
        return (long) LONG.get(buffer, index);
    }

    /**
     * Reads a little-endian {@code long}: eight bytes, the least significant first.
     *
     * @return the {@code long}
     * @throws EOFException if the stream ends before eight bytes
     * @throws IOException if the stream cannot be read
     */
    public long readLongLe() throws IOException {
        var index = take(Long.BYTES);
        return (long) LONG_LE.get(buffer, index);
    }

    /**
     * Reads a big-endian {@code float}, from the bits of a big-endian {@code int}.
     *
     * @return the {@code float}
     * @throws EOFException if the stream ends before four bytes
     * @throws IOException if the stream cannot be read
     * @see Float#intBitsToFloat(int)
     */
    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    /**
     * Reads a little-endian {@code float}, from the bits of a little-endian {@code int}.
     *
     * @return the {@code float}
     * @throws EOFException if the stream ends before four bytes
     * @throws IOException if the stream cannot be read
     * @see Float#intBitsToFloat(int)
     */
    public float readFloatLe() throws IOException {
        return Float.intBitsToFloat(readIntLe());
    }

    /**
     * Reads a big-endian {@code double}, from the bits of a big-endian {@code long}.
     *
     * @return the {@code double}
     * @throws EOFException if the stream ends before eight bytes
     * @throws IOException if the stream cannot be read
     * @see Double#longBitsToDouble(long)
     */
    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Reads a little-endian {@code double}, from the bits of a little-endian {@code long}.
     *
     * @return the {@code double}
     * @throws EOFException if the stream ends before eight bytes
     * @throws IOException if the stream cannot be read
     * @see Double#longBitsToDouble(long)
     */
    public double readDoubleLe() throws IOException {
        return Double.longBitsToDouble(readLongLe());
    }

    /**
     * Reads bytes.
     *
     * @param byteCount the number of bytes to read
     * @return the next {@code byteCount} bytes
     * @throws IllegalArgumentException if {@code byteCount} is negative
     * @throws EOFException if the stream ends before {@code byteCount} bytes
     * @throws IOException if the stream cannot be read
     */
    public Bytes readBytes(int byteCount) throws IOException {
        checkNotNegative("byteCount", byteCount);
        if (byteCount <= buffer.length || in == null) {
            var index = take(byteCount);
            return Bytes.wrap(Arrays.copyOfRange(buffer, index, index + byteCount));
        }
        // A large count, maybe read from the stream itself, isn't allocated before the bytes have been read.
        ensureOpen();
        var buffered = limit - position;
        var head = Arrays.copyOfRange(buffer, position, limit);
        position = limit;
        var tail = in.readNBytes(byteCount - buffered);
        if (tail.length < byteCount - buffered) {
            throw new EOFException();
        }
        var bytes = Arrays.copyOf(head, byteCount);
        System.arraycopy(tail, 0, bytes, buffered, tail.length);
        return Bytes.wrap(bytes);
    }

    /**
     * Reads bytes into an array, filling the part of it given.
     *
     * @param b the array to read into
     * @param offset the index of {@code b} where the first byte goes
     * @param length the number of bytes to read
     * @throws IndexOutOfBoundsException if {@code offset} or {@code length} is negative, or if {@code offset + length}
     *     exceeds the length of {@code b}
     * @throws EOFException if the stream ends before {@code length} bytes
     * @throws IOException if the stream cannot be read
     */
    public void readFully(byte[] b, int offset, int length) throws IOException {
        requireNonNull(b, "b");
        checkFromIndexSize(offset, length, b.length);
        var copied = Math.min(length, limit - position);
        System.arraycopy(buffer, position, b, offset, copied);
        position += copied;
        var remaining = length - copied;
        if (remaining == 0) {
            return;
        }
        ensureOpen();
        if (in != null && remaining >= buffer.length) {
            if (in.readNBytes(b, offset + copied, remaining) < remaining) {
                throw new EOFException();
            }
        } else {
            var index = take(remaining);
            System.arraycopy(buffer, index, b, offset + copied, remaining);
        }
    }

    /**
     * Reads at least one byte into an array, unless the stream has no more, as {@link InputStream#read(byte[], int,
     * int)} does.
     *
     * @param b the array to read into
     * @param offset the index of {@code b} where the first byte goes
     * @param length the number of bytes to read, at most
     * @return the number of bytes read, or -1 at the end of the stream; 0 only if {@code length} is 0
     * @throws IndexOutOfBoundsException if {@code offset} or {@code length} is negative, or if {@code offset + length}
     *     exceeds the length of {@code b}
     * @throws IOException if the stream cannot be read
     */
    public int read(byte[] b, int offset, int length) throws IOException {
        requireNonNull(b, "b");
        checkFromIndexSize(offset, length, b.length);
        if (length == 0) {
            return 0;
        }
        if (position == limit) {
            ensureOpen();
            if (in != null && length >= buffer.length) {
                return in.read(b, offset, length);
            }
            if (!fill(1)) {
                return -1;
            }
        }
        var count = Math.min(length, limit - position);
        System.arraycopy(buffer, position, b, offset, count);
        position += count;
        return count;
    }

    /**
     * Reads the bytes up to the end of the stream.
     *
     * @return the rest of the bytes
     * @throws IOException if the stream cannot be read
     */
    public Bytes readAll() throws IOException {
        ensureOpen();
        var head = Arrays.copyOfRange(buffer, position, limit);
        position = limit;
        if (in == null) {
            return Bytes.wrap(head);
        }
        var tail = in.readAllBytes();
        if (head.length == 0) {
            return Bytes.wrap(tail);
        }
        var bytes = Arrays.copyOf(head, Math.addExact(head.length, tail.length));
        System.arraycopy(tail, 0, bytes, head.length, tail.length);
        return Bytes.wrap(bytes);
    }

    /**
     * Skips bytes.
     *
     * @param byteCount the number of bytes to skip
     * @throws IllegalArgumentException if {@code byteCount} is negative
     * @throws EOFException if the stream ends before {@code byteCount} bytes
     * @throws IOException if the stream cannot be read
     */
    public void skip(long byteCount) throws IOException {
        checkNotNegative("byteCount", byteCount);
        // Reads rather than calls InputStream.skip, which goes past the end of a file without telling.
        var remaining = byteCount;
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
     * Writes the bytes up to the end of the stream to another stream, which stays open.
     *
     * @param out the stream to write to
     * @return the number of bytes written
     * @throws IOException if the stream cannot be read, or {@code out} cannot be written
     */
    public long transferTo(OutputStream out) throws IOException {
        requireNonNull(out, "out");
        ensureOpen();
        var buffered = limit - position;
        out.write(buffer, position, buffered);
        position = limit;
        return in == null ? buffered : buffered + in.transferTo(out);
    }

    /**
     * Reads bytes that encode text in the {@linkplain Charsets#DEFAULT_CHARSET current default charset}.
     *
     * @param byteCount the number of bytes to read
     * @return the text that the next {@code byteCount} bytes encode
     * @throws IllegalArgumentException if {@code byteCount} is negative
     * @throws EOFException if the stream ends before {@code byteCount} bytes
     * @throws IOException if the stream cannot be read
     */
    public String readString(int byteCount) throws IOException {
        return readString(byteCount, Charsets.DEFAULT_CHARSET);
    }

    /**
     * Reads bytes that encode text in a charset. Malformed input is replaced as by {@link String#String(byte[],
     * Charset)}.
     *
     * @param byteCount the number of bytes to read
     * @param charset the charset the text is encoded in
     * @return the text that the next {@code byteCount} bytes encode
     * @throws IllegalArgumentException if {@code byteCount} is negative
     * @throws EOFException if the stream ends before {@code byteCount} bytes
     * @throws IOException if the stream cannot be read
     */
    public String readString(int byteCount, Charset charset) throws IOException {
        checkNotNegative("byteCount", byteCount);
        requireNonNull(charset, "charset");
        if (byteCount <= buffer.length || in == null) {
            var index = take(byteCount);
            return new String(buffer, index, byteCount, charset);
        }
        return readBytes(byteCount).decode(charset);
    }

    /**
     * Reads a line of text encoded in the {@linkplain Charsets#DEFAULT_CHARSET current default charset}.
     *
     * @param maxBytes the number of bytes the line may have, at most, its terminator excluded
     * @return the line, without its terminator, or an empty optional at the end of the stream
     * @throws IllegalArgumentException if {@code maxBytes} is negative
     * @throws IOException if the line has more than {@code maxBytes} bytes, in which case nothing is consumed, or if
     *     the stream cannot be read
     * @see #readLine(int, Charset)
     */
    public Optional<String> readLine(int maxBytes) throws IOException {
        return readLine(maxBytes, Charsets.DEFAULT_CHARSET);
    }

    /**
     * Reads a line of text encoded in a charset. A line ends with a line feed ({@code \n}), a carriage return
     * ({@code \r}), a carriage return followed by a line feed, or the end of the stream, as for
     * {@link java.io.BufferedReader#readLine()}. Malformed input is replaced as by {@link String#String(byte[],
     * Charset)}.
     *
     * <p>Only charsets that encode both terminators as single bytes, as ASCII does, can be read this way, such as UTF-8
     * or ISO-8859-1. Lines in other charsets, such as UTF-16, can be read with {@link CharReader}.
     *
     * @param maxBytes the number of bytes the line may have, at most, its terminator excluded
     * @param charset the charset the text is encoded in
     * @return the line, without its terminator, or an empty optional at the end of the stream
     * @throws IllegalArgumentException if {@code maxBytes} is negative, or if {@code charset} doesn't encode the line
     *     terminators as ASCII does
     * @throws IOException if the line has more than {@code maxBytes} bytes, in which case nothing is consumed, or if
     *     the stream cannot be read
     */
    public Optional<String> readLine(int maxBytes, Charset charset) throws IOException {
        checkNotNegative("maxBytes", maxBytes);
        requireNonNull(charset, "charset");
        checkArgument(
                encodesLineTerminatorsAsAscii(charset), "%s doesn't encode line terminators as ASCII does", charset);
        // Scans the buffered bytes in a tight loop, and fills the buffer only once they are all scanned.
        var scanned = 0;
        while (true) {
            var end = (int) Math.min(limit, (long) position + maxBytes + 1);
            for (int i = position + scanned; i < end; i++) {
                var c = buffer[i];
                if (c == '\n' || c == '\r') {
                    return Optional.of(consumeLine(i - position, c, charset));
                }
            }
            scanned = end - position;
            if (scanned > maxBytes) {
                throw new IOException("Line longer than " + maxBytes + " bytes");
            }
            if (!fill(scanned + 1)) {
                return scanned == 0 ? Optional.empty() : Optional.of(consumeLine(scanned, -1, charset));
            }
        }
    }

    /**
     * Returns a stream that reads the rest of the bytes through this reader, for APIs that take an {@link InputStream}.
     * Reads on the stream and on this reader take turns without skipping or repeating a byte. Closing the stream closes
     * this reader.
     *
     * @return a stream on the rest of the bytes
     */
    public InputStream asInputStream() {
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
        buffer = NO_BYTES;
        position = 0;
        limit = 0;
        if (in != null) {
            in.close();
        }
    }

    private static boolean encodesLineTerminatorsAsAscii(Charset charset) {
        return charset.equals(StandardCharsets.UTF_8)
                || (charset.canEncode() && Arrays.equals("\r\n".getBytes(charset), CRLF));
    }

    // Buffers at least byteCount bytes, reading no more than the stream has at hand; false if it ends first.
    private boolean fill(int byteCount) throws IOException {
        if (limit - position >= byteCount) {
            return true;
        }
        ensureOpen();
        if (in == null) {
            return false;
        }
        if (position == limit) {
            position = 0;
            limit = 0;
        }
        if (buffer.length - position < byteCount) {
            var buffered = limit - position;
            var target = byteCount <= buffer.length
                    ? buffer
                    : new byte[Math.max(byteCount, (int) Math.min(2L * buffer.length, MAX_ARRAY_LENGTH))];
            System.arraycopy(buffer, position, target, 0, buffered);
            buffer = target;
            position = 0;
            limit = buffered;
        }
        while (limit - position < byteCount) {
            var read = in.read(buffer, limit, buffer.length - limit);
            if (read < 0) {
                return false;
            }
            limit += read;
        }
        return true;
    }

    // Consumes a line and its terminator, -1 at the end of the stream, and returns the line.
    private String consumeLine(int length, int terminator, Charset charset) throws IOException {
        var line = new String(buffer, position, length, charset);
        position += length;
        if (terminator != -1) {
            position++;
            if (terminator == '\r' && fill(1) && buffer[position] == '\n') {
                position++;
            }
        }
        return line;
    }

    // Consumes byteCount bytes and returns the index of the first in the buffer, which it may replace: callers read
    // the buffer field after calling it.
    private int take(int byteCount) throws IOException {
        if (!fill(byteCount)) {
            position = limit;
            throw new EOFException();
        }
        var index = position;
        position += byteCount;
        return index;
    }

    // Whether the buffered bytes at an offset are those of other, which must all be buffered.
    private boolean regionEquals(int offset, Bytes other) {
        var start = position + offset;
        return Arrays.equals(
                buffer, start, start + other.size(), other.array(), other.offset(), other.offset() + other.size());
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Reader closed");
        }
    }

    private static void checkNotNegative(String name, long value) {
        checkArgument(value >= 0, "%s must not be negative: %s", name, value);
    }

    private final class View extends InputStream {

        @Override
        public int read() throws IOException {
            return fill(1) ? Byte.toUnsignedInt(buffer[position++]) : -1;
        }

        @Override
        public int read(byte[] b, int offset, int length) throws IOException {
            return ByteReader.this.read(b, offset, length);
        }

        @Override
        public long skip(long n) throws IOException {
            if (n <= 0 || !fill(1)) {
                return 0;
            }
            var skipped = (int) Math.min(n, limit - position);
            position += skipped;
            return skipped;
        }

        @Override
        public int available() throws IOException {
            ensureOpen();
            var available = (long) limit - position + (in == null ? 0 : in.available());
            return (int) Math.min(available, Integer.MAX_VALUE);
        }

        @Override
        public long transferTo(OutputStream out) throws IOException {
            return ByteReader.this.transferTo(out);
        }

        @Override
        public void close() throws IOException {
            ByteReader.this.close();
        }
    }
}
