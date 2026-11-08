package be.imgn.gaia.core.io;

import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.checkFromToIndex;
import static java.util.Objects.checkIndex;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.base.Charsets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;

/**
 * An immutable sequence of bytes, usable as a value: two instances with the same bytes are equal, and instances are
 * ordered by their bytes.
 *
 * <p>Arrays given to the factory methods are copied, and arrays handed out are copies, so no one can change the bytes
 * of an instance. A {@linkplain #slice(int, int) slice} shares the bytes of the instance it comes from.
 *
 * <pre>{@code
 * var checksum = Bytes.encode(text).digest("SHA-256").toHex();
 * var frame = Bytes.build(out -> out.writeShort(type).writeInt(body.size()).write(body));
 * }</pre>
 *
 * @since 0.1.3
 */
public final class Bytes implements Comparable<Bytes> {

    private static final Bytes EMPTY = new Bytes(new byte[0], 0, 0);

    // The number of bytes that toString() shows.
    private static final int TO_STRING_LIMIT = 32;

    private final byte[] array;
    private final int offset;
    private final int size;

    // 0 until computed, like String's.
    private int hash;

    private Bytes(byte[] array, int offset, int size) {
        this.array = array;
        this.offset = offset;
        this.size = size;
    }

    // Takes ownership of the array, without copying it.
    static Bytes wrap(byte[] array) {
        return array.length == 0 ? EMPTY : new Bytes(array, 0, array.length);
    }

    // Takes ownership of part of the array, without copying it.
    static Bytes wrap(byte[] array, int offset, int size) {
        return size == 0 ? EMPTY : new Bytes(array, offset, size);
    }

    /**
     * Returns the instance with no bytes.
     *
     * @return the empty instance
     */
    public static Bytes empty() {
        return EMPTY;
    }

    /**
     * Returns an instance with a copy of bytes.
     *
     * @param bytes the bytes
     * @return an instance with {@code bytes}
     */
    public static Bytes of(byte... bytes) {
        requireNonNull(bytes, "bytes");
        return wrap(bytes.clone());
    }

    /**
     * Returns an instance with a copy of part of an array.
     *
     * @param bytes the array
     * @param offset the index of the first byte to copy
     * @param length the number of bytes to copy
     * @return an instance with the bytes from {@code offset}, included, to {@code offset + length}, excluded
     * @throws IndexOutOfBoundsException if {@code offset} or {@code length} is negative, or if {@code offset + length}
     *     exceeds the length of {@code bytes}
     */
    public static Bytes copyOf(byte[] bytes, int offset, int length) {
        requireNonNull(bytes, "bytes");
        checkFromIndexSize(offset, length, bytes.length);
        return wrap(Arrays.copyOfRange(bytes, offset, offset + length));
    }

    /**
     * Returns an instance with a copy of the remaining bytes of a buffer, from its position to its limit. The position
     * of the buffer does not change.
     *
     * @param buffer the buffer
     * @return an instance with the remaining bytes of {@code buffer}
     */
    public static Bytes copyOf(ByteBuffer buffer) {
        requireNonNull(buffer, "buffer");
        var bytes = new byte[buffer.remaining()];
        buffer.get(buffer.position(), bytes);
        return wrap(bytes);
    }

    /**
     * Returns the bytes that encode characters in the {@linkplain Charsets#DEFAULT_CHARSET current default charset}.
     *
     * @param chars the characters
     * @return the bytes that encode {@code chars}
     */
    public static Bytes encode(CharSequence chars) {
        requireNonNull(chars, "chars");
        return encode(chars, Charsets.DEFAULT_CHARSET);
    }

    /**
     * Returns the bytes that encode characters in a charset. Characters that the charset cannot encode, and unpaired
     * surrogates, are replaced as by {@link String#getBytes(Charset)}.
     *
     * @param chars the characters
     * @param charset the charset to encode them in
     * @return the bytes that encode {@code chars}
     */
    public static Bytes encode(CharSequence chars, Charset charset) {
        requireNonNull(chars, "chars");
        requireNonNull(charset, "charset");
        return wrap(chars.toString().getBytes(charset));
    }

    /**
     * Returns the bytes that hexadecimal digits stand for, two digits per byte, in upper or lower case.
     *
     * @param hex the hexadecimal digits
     * @return the bytes that {@code hex} stands for
     * @throws IllegalArgumentException if {@code hex} has an odd length, or a character that isn't a hexadecimal digit
     */
    public static Bytes fromHex(CharSequence hex) {
        requireNonNull(hex, "hex");
        return wrap(HexFormat.of().parseHex(hex));
    }

    /**
     * Returns the bytes that Base64 text stands for, in the basic alphabet of RFC 4648. The padding is optional.
     *
     * @param base64 the Base64 text
     * @return the bytes that {@code base64} stands for
     * @throws IllegalArgumentException if {@code base64} isn't valid Base64
     * @see Base64#getDecoder()
     */
    public static Bytes fromBase64(CharSequence base64) {
        requireNonNull(base64, "base64");
        return wrap(Base64.getDecoder().decode(base64.toString()));
    }

    /**
     * Returns the bytes that Base64 text stands for, in the URL and file name safe alphabet of RFC 4648. The padding is
     * optional.
     *
     * @param base64 the Base64 text
     * @return the bytes that {@code base64} stands for
     * @throws IllegalArgumentException if {@code base64} isn't valid Base64
     * @see Base64#getUrlDecoder()
     */
    public static Bytes fromBase64Url(CharSequence base64) {
        requireNonNull(base64, "base64");
        return wrap(Base64.getUrlDecoder().decode(base64.toString()));
    }

    /**
     * Returns the bytes of instances one after the other.
     *
     * @param values the instances
     * @return the bytes of {@code values}, in order
     */
    public static Bytes concat(Bytes... values) {
        requireNonNull(values, "values");
        var size = 0;
        for (var value : values) {
            size = Math.addExact(size, requireNonNull(value, "values contains null").size);
        }
        var bytes = new byte[size];
        var position = 0;
        for (var value : values) {
            System.arraycopy(value.array, value.offset, bytes, position, value.size);
            position += value.size;
        }
        return wrap(bytes);
    }

    /**
     * Returns the bytes that a callback writes to a writer in memory.
     *
     * @param content the callback that writes the bytes
     * @return the bytes that {@code content} writes
     * @throws UncheckedIOException if {@code content} throws an {@link IOException}, which then is its cause; writing
     *     to memory never fails
     */
    public static Bytes build(Content content) {
        requireNonNull(content, "content");
        var out = new ByteArrayOutputStream();
        var writer = ByteWriter.of(out);
        try {
            content.writeTo(writer);
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return wrap(out.toByteArray());
    }

    /**
     * Returns the number of bytes.
     *
     * @return the number of bytes
     */
    public int size() {
        return size;
    }

    /**
     * Tells whether there are no bytes.
     *
     * @return whether the size is 0
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the byte at an index.
     *
     * @param index the index, from 0
     * @return the byte at {@code index}
     * @throws IndexOutOfBoundsException if {@code index} is negative, or not less than the size
     */
    public byte get(int index) {
        checkIndex(index, size);
        return array[offset + index];
    }

    /**
     * Returns the bytes between two indexes. The slice shares the bytes of this instance instead of copying them, so a
     * small slice keeps all of them in memory; {@code Bytes.of(slice.toByteArray())} keeps only its own.
     *
     * @param fromIndex the index of the first byte, included
     * @param toIndex the index after the last byte, excluded
     * @return the bytes from {@code fromIndex} to {@code toIndex}
     * @throws IndexOutOfBoundsException if {@code fromIndex} is negative, greater than {@code toIndex}, or if
     *     {@code toIndex} is greater than the size
     */
    public Bytes slice(int fromIndex, int toIndex) {
        checkFromToIndex(fromIndex, toIndex, size);
        return fromIndex == 0 && toIndex == size ? this : wrap(array, offset + fromIndex, toIndex - fromIndex);
    }

    /**
     * Tells whether these bytes start with others.
     *
     * @param prefix the bytes to look for
     * @return whether the first bytes of this instance are those of {@code prefix}
     */
    public boolean startsWith(Bytes prefix) {
        requireNonNull(prefix, "prefix");
        return regionEquals(0, prefix);
    }

    /**
     * Tells whether these bytes end with others.
     *
     * @param suffix the bytes to look for
     * @return whether the last bytes of this instance are those of {@code suffix}
     */
    public boolean endsWith(Bytes suffix) {
        requireNonNull(suffix, "suffix");
        return regionEquals(size - suffix.size, suffix);
    }

    /**
     * Returns the index of the first occurrence of a byte.
     *
     * @param b the byte to look for
     * @return the index of the first {@code b}, or -1 if there is none
     */
    public int indexOf(byte b) {
        for (int i = 0; i < size; i++) {
            if (array[offset + i] == b) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the first occurrence of other bytes.
     *
     * @param target the bytes to look for
     * @return the index where the first occurrence of {@code target} starts, 0 if {@code target} is empty, or -1 if
     *     there is none
     */
    public int indexOf(Bytes target) {
        requireNonNull(target, "target");
        for (int i = 0; i <= size - target.size; i++) {
            if (regionEquals(i, target)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns a copy of the bytes. Each call returns a new array.
     *
     * @return a new array with the bytes
     */
    public byte[] toByteArray() {
        return Arrays.copyOfRange(array, offset, offset + size);
    }

    /**
     * Returns a read-only buffer on the bytes, without copying them. Its position is 0, and its limit and capacity are
     * the size.
     *
     * @return a read-only buffer on the bytes
     */
    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(array, offset, size).slice().asReadOnlyBuffer();
    }

    /**
     * Writes the bytes to a stream, which stays open.
     *
     * @param out the stream to write to
     * @throws IOException if {@code out} cannot be written
     */
    public void writeTo(OutputStream out) throws IOException {
        requireNonNull(out, "out");
        out.write(array, offset, size);
    }

    /**
     * Returns the characters that the bytes encode in the {@linkplain Charsets#DEFAULT_CHARSET current default
     * charset}.
     *
     * @return the decoded characters
     */
    public String decode() {
        return decode(Charsets.DEFAULT_CHARSET);
    }

    /**
     * Returns the characters that the bytes encode in a charset. Malformed input is replaced as by
     * {@link String#String(byte[], Charset)}.
     *
     * @param charset the charset the bytes are encoded in
     * @return the decoded characters
     */
    public String decode(Charset charset) {
        requireNonNull(charset, "charset");
        return new String(array, offset, size, charset);
    }

    /**
     * Returns the bytes as hexadecimal digits in lower case, two per byte.
     *
     * @return the hexadecimal digits
     */
    public String toHex() {
        return HexFormat.of().formatHex(array, offset, offset + size);
    }

    /**
     * Returns the bytes as Base64 text in the basic alphabet of RFC 4648, with padding.
     *
     * @return the Base64 text
     * @see Base64#getEncoder()
     */
    public String toBase64() {
        return Base64.getEncoder().encodeToString(wholeArray());
    }

    /**
     * Returns the bytes as Base64 text in the URL and file name safe alphabet of RFC 4648, with padding.
     *
     * @return the Base64 text
     * @see Base64#getUrlEncoder()
     */
    public String toBase64Url() {
        return Base64.getUrlEncoder().encodeToString(wholeArray());
    }

    /**
     * Returns the digest of the bytes, such as their SHA-256 hash.
     *
     * @param algorithm the name of the digest algorithm, such as {@code "SHA-256"}
     * @return the digest of the bytes
     * @throws IllegalArgumentException if no provider supports {@code algorithm}
     * @see MessageDigest#getInstance(String)
     */
    public Bytes digest(String algorithm) {
        requireNonNull(algorithm, "algorithm");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("No such digest algorithm: " + algorithm, e);
        }
        digest.update(array, offset, size);
        return wrap(digest.digest());
    }

    /**
     * Tells whether an object is an instance with the same bytes. The comparison takes longer the more leading bytes
     * match, so it can reveal secrets to a timing attack; {@link MessageDigest#isEqual(byte[], byte[])} doesn't.
     *
     * @param object the object to compare to
     * @return whether {@code object} is an instance with the same bytes
     */
    @Override
    public boolean equals(@Nullable Object object) {
        return object instanceof Bytes other
                && Arrays.equals(array, offset, offset + size, other.array, other.offset, other.offset + other.size);
    }

    /**
     * Returns a hash code of the bytes, the same as {@link Arrays#hashCode(byte[])} on a copy of them.
     *
     * @return a hash code of the bytes
     */
    @Override
    public int hashCode() {
        var h = hash;
        if (h == 0) {
            h = 1;
            for (int i = offset; i < offset + size; i++) {
                h = 31 * h + array[i];
            }
            hash = h;
        }
        return h;
    }

    /**
     * Compares these bytes with others, one by one as unsigned values, then by size.
     *
     * @param other the bytes to compare to
     * @return a negative value, 0 or a positive value if these bytes come before {@code other}, are equal or come after
     * @see Arrays#compareUnsigned(byte[], int, int, byte[], int, int)
     */
    @Override
    public int compareTo(Bytes other) {
        requireNonNull(other, "other");
        return Arrays.compareUnsigned(
                array, offset, offset + size, other.array, other.offset, other.offset + other.size);
    }

    /**
     * Returns a description of the bytes, for debugging, such as {@code Bytes[size=3, hex=cafeba]}. Only the first 32
     * bytes are shown.
     *
     * @return a description of the bytes
     */
    @Override
    public String toString() {
        var shown = Math.min(size, TO_STRING_LIMIT);
        var hex = HexFormat.of().formatHex(array, offset, offset + shown);
        return "Bytes[size=" + size + ", hex=" + hex + (shown < size ? "...]" : "]");
    }

    // The array that holds the bytes, from offset() for size() bytes. Callers must not change it.
    byte[] array() {
        return array;
    }

    int offset() {
        return offset;
    }

    private byte[] wholeArray() {
        return offset == 0 && size == array.length ? array : toByteArray();
    }

    private boolean regionEquals(int index, Bytes other) {
        return index >= 0
                && index + other.size <= size
                && Arrays.equals(
                        array,
                        offset + index,
                        offset + index + other.size,
                        other.array,
                        other.offset,
                        other.offset + other.size);
    }

    /**
     * Writes bytes to a writer, for {@link #build(Content)}.
     *
     * @since 0.1.3
     */
    @FunctionalInterface
    public interface Content {

        /**
         * Writes bytes to {@code out}.
         *
         * @param out the writer to write to, which may be closed
         * @throws IOException if the content cannot be written
         */
        void writeTo(ByteWriter out) throws IOException;
    }
}
