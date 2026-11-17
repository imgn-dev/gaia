package be.imgn.gaia.core.io;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.io.internal.OpenOptions;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.OptionalLong;

/** A source that reads a file. */
final class PathSource implements ByteSource {

    // The size of the runs read from the file: larger ones are no faster, and the JDK copies each through a native
    // buffer of that size, which it keeps for the thread.
    private static final int RUN_SIZE = 64 * 1024;

    // The largest array that every JVM can allocate.
    private static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    private final Path file;
    private final OpenOption[] options;
    private final LinkOption[] linkOptions;

    /**
     * Creates a source that reads a file.
     *
     * @param file the file to read
     * @param options how to open the file
     */
    PathSource(Path file, OpenOption... options) {
        this.file = requireNonNull(file, "file");
        this.options = OpenOptions.copyOf(options);
        this.linkOptions = Arrays.asList(this.options).contains(LinkOption.NOFOLLOW_LINKS)
                ? new LinkOption[] {LinkOption.NOFOLLOW_LINKS}
                : new LinkOption[0];
    }

    @Override
    public ByteReader openReader() throws IOException {
        return ByteReader.of(Files.newInputStream(file, options));
    }

    // Reads the file into an array of its size, a run at a time: faster than through a stream, and than
    // Files.readAllBytes, which reads it in a single call. Fails at once on a file too large for an array, rather than
    // read it first.
    @Override
    public Bytes read() throws IOException {
        try (var channel = Files.newByteChannel(file, options)) {
            var bytes = new byte[arraySize(channel.size())];
            var count = 0;
            while (count < bytes.length) {
                var read = channel.read(ByteBuffer.wrap(bytes, count, Math.min(RUN_SIZE, bytes.length - count)));
                if (read < 0) {
                    return Bytes.wrap(Arrays.copyOf(bytes, count));
                }
                count += read;
            }
            // A file that grew since, or that reports a size of 0, as those of /proc do, has more.
            var next = ByteBuffer.allocate(1);
            if (channel.read(next) < 0) {
                return Bytes.wrap(bytes);
            }
            var rest = Channels.newInputStream(channel).readAllBytes();
            var all = Arrays.copyOf(bytes, arraySize((long) count + 1 + rest.length));
            all[count] = next.get(0);
            System.arraycopy(rest, 0, all, count + 1, rest.length);
            return Bytes.wrap(all);
        }
    }

    private static int arraySize(long size) {
        if (size > MAX_ARRAY_LENGTH) {
            throw new OutOfMemoryError("Required array size too large: " + size);
        }
        return (int) size;
    }

    // Only a regular file has a size to trust: special files, such as those of /proc on Linux, report a size of 0
    // whatever they hold.
    @Override
    public OptionalLong sizeIfKnown() {
        try {
            var attributes = Files.readAttributes(file, BasicFileAttributes.class, linkOptions);
            return attributes.isRegularFile() ? OptionalLong.of(attributes.size()) : OptionalLong.empty();
        } catch (IOException e) {
            return OptionalLong.empty();
        }
    }
}
