package be.imgn.gaia.memfs;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.memfs.internal.MemoryFileSystemProvider;
import java.nio.file.FileSystem;
import java.util.UUID;

/**
 * Creates file systems in memory. A file system lives until it is closed; its files are then gone.
 *
 * <pre>{@code
 * try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
 *     Path file = fileSystem.getPath("/work/hello.txt");
 *     Files.writeString(file, "Hello");
 * }
 * }</pre>
 *
 * <p>Each file system has a name, and its paths have URIs of the {@code mem} scheme with that name:
 * {@code mem://test/work/hello.txt} for the path {@code /work/hello.txt} of the file system {@code test}, or
 * {@code mem://test/C:/work/hello.txt} for {@code C:\work\hello.txt} on a Windows-style one. While the file system is
 * open, {@link java.nio.file.Path#of(java.net.URI)} finds its paths from such URIs, and
 * {@link java.nio.file.FileSystems#getFileSystem(java.net.URI)} finds it from {@code mem://test}.
 * {@link java.nio.file.FileSystems#newFileSystem(java.net.URI, java.util.Map)} creates one too, given its
 * {@link Configuration} under the key {@code "configuration"}, or with {@link Configuration#unix()} without it.
 *
 * <p>The file systems support the whole {@code java.nio.file} API, as the {@link Configuration} sets it up, except
 * {@link java.nio.file.Path#toFile()} and {@link java.nio.channels.FileChannel#map}, which throw
 * {@link UnsupportedOperationException}: neither can work on files in memory. They are thread-safe.
 *
 * @since 0.1.1
 */
public final class MemoryFileSystems {

    private MemoryFileSystems() {}

    /**
     * Creates a file system with a unique name and the {@link Configuration#unix()} configuration.
     *
     * @return the file system
     */
    public static FileSystem newFileSystem() {
        return newFileSystem(Configuration.unix());
    }

    /**
     * Creates a file system with a unique name.
     *
     * @param configuration the configuration
     * @return the file system
     */
    public static FileSystem newFileSystem(Configuration configuration) {
        requireNonNull(configuration, "configuration");
        return newFileSystem(UUID.randomUUID().toString(), configuration);
    }

    /**
     * Creates a file system.
     *
     * @param name the name of the file system, which its URIs have as authority
     * @param configuration the configuration
     * @return the file system
     * @throws IllegalArgumentException if the name has another character than letters, digits and {@code . _ ~ -}
     * @throws java.nio.file.FileSystemAlreadyExistsException if a file system of that name is open
     */
    public static FileSystem newFileSystem(String name, Configuration configuration) {
        requireNonNull(name, "name");
        requireNonNull(configuration, "configuration");
        return MemoryFileSystemProvider.instance().newFileSystem(name, configuration);
    }
}
