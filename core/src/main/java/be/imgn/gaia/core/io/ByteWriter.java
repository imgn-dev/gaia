package be.imgn.gaia.core.io;

import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.base.Charsets;
import be.imgn.gaia.core.io.internal.Streams;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.Charset;

/**
 * Writes a stream of bytes: numbers in either byte order, runs of bytes and text.
 *
 * <p>A writer holds a buffer, which it writes to its stream when it is full, on {@link #flush()} and on
 * {@link #close()}. Every write method returns the writer, so calls chain.
 *
 * <p>Methods that write a number write it in big-endian order, as {@link java.io.DataOutputStream} does; those whose
 * name ends in {@code Le} write it in little-endian order. Methods that take an {@code int} for a smaller type write
 * its low bits.
 *
 * <pre>{@code
 * try (var out = ByteSink.of(file).openWriter()) {
 *     out.write(MAGIC).writeShort(VERSION).writeIntLe(entries.size());
 *     for (var entry : entries) {
 *         out.writeLongLe(entry.offset()).writeString(entry.name()).writeByte(0);
 *     }
 * }
 * }</pre>
 *
 * <p>A writer owns its stream: {@link #close()} closes it, and any write after that throws {@link IOException}. A
 * writer is not safe for use by several threads at once.
 *
 * @since 0.1.3
 */
public final class ByteWriter implements Closeable, Flushable {

    private static final VarHandle SHORT = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle SHORT_LE =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle INT_LE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle LONG_LE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    private static final byte[] NO_BYTES = {};

    private final OutputStream out;
    // Empty once closed, so that any write that needs room goes through flushBuffer(), which then fails.
    private byte[] buffer = new byte[Streams.BUFFER_SIZE];
    private int count;
    private boolean closed;

    private ByteWriter(OutputStream out) {
        this.out = out;
    }

    /**
     * Returns a writer that writes to a stream, which it then owns. If {@code out} is the {@linkplain #asOutputStream()
     * stream view} of a writer, returns that writer rather than buffering its bytes again.
     *
     * @param out the stream to write to
     * @return a writer that writes to {@code out}
     */
    public static ByteWriter of(OutputStream out) {
        requireNonNull(out, "out");
        if (out instanceof View view) {
            return view.owner();
        }
        return new ByteWriter(out);
    }

    /**
     * Writes a byte.
     *
     * @param b the byte, as its low 8 bits
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    public ByteWriter writeByte(int b) throws IOException {
        var index = reserve(Byte.BYTES);
        buffer[index] = (byte) b;
        return this;
    }

    /**
     * Writes a big-endian {@code short}: two bytes, the most significant first.
     *
     * @param s the {@code short}, as the low 16 bits of an {@code int}
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    public ByteWriter writeShort(int s) throws IOException {
        var index = reserve(Short.BYTES);
        SHORT.set(buffer, index, (short) s);
        return this;
    }

    /**
     * Writes a little-endian {@code short}: two bytes, the least significant first.
     *
     * @param s the {@code short}, as the low 16 bits of an {@code int}
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    public ByteWriter writeShortLe(int s) throws IOException {
        var index = reserve(Short.BYTES);
        SHORT_LE.set(buffer, index, (short) s);
        return this;
    }

    /**
     * Writes a big-endian {@code int}: four bytes, the most significant first.
     *
     * @param i the {@code int}
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    public ByteWriter writeInt(int i) throws IOException {
        var index = reserve(Integer.BYTES);
        INT.set(buffer, index, i);
        return this;
    }

    /**
     * Writes a little-endian {@code int}: four bytes, the least significant first.
     *
     * @param i the {@code int}
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    public ByteWriter writeIntLe(int i) throws IOException {
        var index = reserve(Integer.BYTES);
        INT_LE.set(buffer, index, i);
        return this;
    }

    /**
     * Writes a big-endian {@code long}: eight bytes, the most significant first.
     *
     * @param l the {@code long}
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    public ByteWriter writeLong(long l) throws IOException {
        var index = reserve(Long.BYTES);
        LONG.set(buffer, index, l);
        return this;
    }

    /**
     * Writes a little-endian {@code long}: eight bytes, the least significant first.
     *
     * @param l the {@code long}
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    public ByteWriter writeLongLe(long l) throws IOException {
        var index = reserve(Long.BYTES);
        LONG_LE.set(buffer, index, l);
        return this;
    }

    /**
     * Writes a big-endian {@code float}, as the bits of a big-endian {@code int}.
     *
     * @param f the {@code float}
     * @return this writer
     * @throws IOException if the stream cannot be written
     * @see Float#floatToIntBits(float)
     */
    public ByteWriter writeFloat(float f) throws IOException {
        return writeInt(Float.floatToIntBits(f));
    }

    /**
     * Writes a little-endian {@code float}, as the bits of a little-endian {@code int}.
     *
     * @param f the {@code float}
     * @return this writer
     * @throws IOException if the stream cannot be written
     * @see Float#floatToIntBits(float)
     */
    public ByteWriter writeFloatLe(float f) throws IOException {
        return writeIntLe(Float.floatToIntBits(f));
    }

    /**
     * Writes a big-endian {@code double}, as the bits of a big-endian {@code long}.
     *
     * @param d the {@code double}
     * @return this writer
     * @throws IOException if the stream cannot be written
     * @see Double#doubleToLongBits(double)
     */
    public ByteWriter writeDouble(double d) throws IOException {
        return writeLong(Double.doubleToLongBits(d));
    }

    /**
     * Writes a little-endian {@code double}, as the bits of a little-endian {@code long}.
     *
     * @param d the {@code double}
     * @return this writer
     * @throws IOException if the stream cannot be written
     * @see Double#doubleToLongBits(double)
     */
    public ByteWriter writeDoubleLe(double d) throws IOException {
        return writeLongLe(Double.doubleToLongBits(d));
    }

    /**
     * Writes the bytes of an array.
     *
     * @param b the array
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    public ByteWriter write(byte[] b) throws IOException {
        requireNonNull(b, "b");
        writeBytes(b, 0, b.length);
        return this;
    }

    /**
     * Writes part of an array.
     *
     * @param b the array
     * @param offset the index of the first byte to write
     * @param length the number of bytes to write
     * @return this writer
     * @throws IndexOutOfBoundsException if {@code offset} or {@code length} is negative, or if {@code offset + length}
     *     exceeds the length of {@code b}
     * @throws IOException if the stream cannot be written
     */
    public ByteWriter write(byte[] b, int offset, int length) throws IOException {
        requireNonNull(b, "b");
        checkFromIndexSize(offset, length, b.length);
        writeBytes(b, offset, length);
        return this;
    }

    /**
     * Writes bytes.
     *
     * @param bytes the bytes
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    public ByteWriter write(Bytes bytes) throws IOException {
        requireNonNull(bytes, "bytes");
        writeBytes(bytes.array(), bytes.offset(), bytes.size());
        return this;
    }

    /**
     * Writes text encoded in the {@linkplain Charsets#DEFAULT_CHARSET current default charset}.
     *
     * @param chars the text
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    public ByteWriter writeString(CharSequence chars) throws IOException {
        return writeString(chars, Charsets.DEFAULT_CHARSET);
    }

    /**
     * Writes text encoded in a charset. Characters that the charset cannot encode, and unpaired surrogates, are
     * replaced as by {@link String#getBytes(Charset)}.
     *
     * @param chars the text
     * @param charset the charset to encode it in
     * @return this writer
     * @throws IOException if the stream cannot be written
     */
    public ByteWriter writeString(CharSequence chars, Charset charset) throws IOException {
        requireNonNull(chars, "chars");
        requireNonNull(charset, "charset");
        var bytes = chars.toString().getBytes(charset);
        writeBytes(bytes, 0, bytes.length);
        return this;
    }

    /**
     * Writes the bytes of a stream up to its end. The stream stays open.
     *
     * @param in the stream to read
     * @return the number of bytes written
     * @throws IOException if {@code in} cannot be read, or the stream of this writer cannot be written
     */
    public long transferFrom(InputStream in) throws IOException {
        requireNonNull(in, "in");
        ensureOpen();
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
     * Writes the buffered bytes to the stream, and flushes the stream.
     *
     * @throws IOException if the stream cannot be written
     */
    @Override
    public void flush() throws IOException {
        flushBuffer();
        out.flush();
    }

    /**
     * Returns a stream that writes through this writer, for APIs that take an {@link OutputStream}. Writes on the
     * stream and on this writer go through the same buffer, in the order they are made. Closing the stream closes this
     * writer.
     *
     * @return a stream that writes through this writer
     */
    public OutputStream asOutputStream() {
        return new View();
    }

    /**
     * Writes the buffered bytes to the stream, then closes it, even if writing fails. Closing a writer again has no
     * effect.
     *
     * @throws IOException if the stream cannot be written or closed; if both fail, the second exception is suppressed
     *     by the first
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        try (out) {
            flushBuffer();
        } finally {
            closed = true;
            buffer = NO_BYTES;
            count = 0;
        }
    }

    // Makes room for byteCount bytes, which must fit in an empty buffer, and returns the index where they go.
    private int reserve(int byteCount) throws IOException {
        if (buffer.length - count < byteCount) {
            flushBuffer();
        }
        var index = count;
        count += byteCount;
        return index;
    }

    private void writeBytes(byte[] b, int offset, int length) throws IOException {
        if (length > buffer.length - count) {
            flushBuffer();
            if (length >= buffer.length) {
                out.write(b, offset, length);
                return;
            }
        }
        System.arraycopy(b, offset, buffer, count, length);
        count += length;
    }

    private void flushBuffer() throws IOException {
        ensureOpen();
        if (count > 0) {
            out.write(buffer, 0, count);
            count = 0;
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Writer closed");
        }
    }

    private final class View extends OutputStream {

        ByteWriter owner() {
            return ByteWriter.this;
        }

        @Override
        public void write(int b) throws IOException {
            writeByte(b);
        }

        @Override
        public void write(byte[] b, int offset, int length) throws IOException {
            ByteWriter.this.write(b, offset, length);
        }

        @Override
        public void flush() throws IOException {
            ByteWriter.this.flush();
        }

        @Override
        public void close() throws IOException {
            ByteWriter.this.close();
        }
    }
}
