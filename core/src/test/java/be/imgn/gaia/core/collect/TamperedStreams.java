package be.imgn.gaia.core.collect;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Tampers with serialized streams, as an attacker would, to check that deserializing them fails cleanly. */
final class TamperedStreams {

    private static final byte TC_BLOCKDATA = 0x77;
    private static final byte TC_STRING = 0x74;

    private TamperedStreams() {}

    /**
     * Replaces the first {@code int} with the given value that a {@code writeObject} method wrote between two objects.
     *
     * @param stream the serialized stream
     * @param value the {@code int} to replace
     * @param replacement the value to write in its place
     * @return a tampered copy of the stream
     * @throws IllegalArgumentException if the stream has no such {@code int}
     */
    static byte[] replacingFirstInt(byte[] stream, int value, int replacement) {
        var block = ByteBuffer.allocate(6)
                .put(TC_BLOCKDATA)
                .put((byte) 4)
                .putInt(value)
                .array();
        for (var i = 0; i + block.length <= stream.length; i++) {
            if (ByteBuffer.wrap(stream, i, block.length).equals(ByteBuffer.wrap(block))) {
                var tampered = stream.clone();
                ByteBuffer.wrap(tampered).putInt(i + 2, replacement);
                return tampered;
            }
        }
        throw new IllegalArgumentException("no block holding " + value);
    }

    /**
     * Replaces the first string object with another of the same length.
     *
     * @param stream the serialized stream
     * @param value the ASCII string to replace
     * @param replacement the ASCII string to write in its place, as long as {@code value}
     * @return a tampered copy of the stream
     * @throws IllegalArgumentException if the stream has no such string, or the strings differ in length
     */
    static byte[] replacingFirstString(byte[] stream, String value, String replacement) {
        if (value.length() != replacement.length()) {
            throw new IllegalArgumentException("strings of different lengths");
        }
        var string = ByteBuffer.allocate(3 + value.length())
                .put(TC_STRING)
                .putShort((short) value.length())
                .put(value.getBytes(StandardCharsets.US_ASCII))
                .array();
        for (var i = 0; i + string.length <= stream.length; i++) {
            if (ByteBuffer.wrap(stream, i, string.length).equals(ByteBuffer.wrap(string))) {
                var tampered = stream.clone();
                ByteBuffer.wrap(tampered).put(i + 3, replacement.getBytes(StandardCharsets.US_ASCII));
                return tampered;
            }
        }
        throw new IllegalArgumentException("no string " + value);
    }
}
