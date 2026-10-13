package be.imgn.gaia.core.io.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.util.Arrays;

/** Operations on streams that the JDK lacks. */
public final class Streams {

    /** The size of the buffers the operations use. */
    public static final int BUFFER_SIZE = 8192;

    private Streams() {}

    /**
     * Copies characters up to the end of {@code in}, leaving both open.
     *
     * @param in what to read
     * @param out what to write to
     * @return the number of characters copied
     * @throws IOException if {@code in} cannot be read or {@code out} cannot be written
     */
    public static long copy(Readable in, Appendable out) throws IOException {
        var buffer = CharBuffer.allocate(BUFFER_SIZE);
        var total = 0L;
        while (in.read(buffer) != -1) {
            buffer.flip();
            total += buffer.remaining();
            out.append(buffer);
            buffer.clear();
        }
        return total;
    }

    /**
     * Tells whether two streams have the same bytes up to their end, leaving both open.
     *
     * @param in a stream
     * @param other the other stream
     * @return whether both streams have the same bytes
     * @throws IOException if either stream cannot be read
     */
    public static boolean contentEquals(InputStream in, InputStream other) throws IOException {
        var buffer = new byte[BUFFER_SIZE];
        var otherBuffer = new byte[BUFFER_SIZE];
        while (true) {
            var read = in.readNBytes(buffer, 0, BUFFER_SIZE);
            var otherRead = other.readNBytes(otherBuffer, 0, BUFFER_SIZE);
            if (read != otherRead || !Arrays.equals(buffer, 0, read, otherBuffer, 0, read)) {
                return false;
            }
            if (read < BUFFER_SIZE) {
                return true;
            }
        }
    }

    /**
     * Skips bytes of a stream, stopping early at its end.
     *
     * @param in the stream
     * @param count the number of bytes to skip
     * @throws IOException if the stream cannot be read
     */
    // Unlike InputStream.skipNBytes, reaching the end is not an error. InputStream.skip may skip nothing before the
    // end, so a byte is read to tell whether the end is reached.
    public static void skipUpTo(InputStream in, long count) throws IOException {
        var remaining = count;
        while (remaining > 0) {
            var skipped = in.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else if (in.read() == -1) {
                return;
            } else {
                remaining--;
            }
        }
    }
}
