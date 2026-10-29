package be.imgn.gaia.core.collect;

import java.nio.ByteBuffer;

/** Tampers with serialized streams, as an attacker would, to check that deserializing them fails cleanly. */
final class TamperedStreams {

    private static final byte TC_BLOCKDATA = 0x77;

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
}
