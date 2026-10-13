package be.imgn.gaia.core.io.internal;

import be.imgn.gaia.core.io.ByteSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalLong;

/** A source that reads a file. */
public final class PathSource implements ByteSource {

    private final Path file;
    private final OpenOption[] options;
    private final LinkOption[] linkOptions;

    /**
     * Creates a source that reads a file.
     *
     * @param file the file to read
     * @param options how to open the file
     */
    public PathSource(Path file, OpenOption... options) {
        this.file = Objects.requireNonNull(file, "file");
        this.options = OpenOptions.copyOf(options);
        this.linkOptions = Arrays.asList(this.options).contains(LinkOption.NOFOLLOW_LINKS)
                ? new LinkOption[] {LinkOption.NOFOLLOW_LINKS}
                : new LinkOption[0];
    }

    @Override
    public InputStream openStream() throws IOException {
        return Files.newInputStream(file, options);
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
