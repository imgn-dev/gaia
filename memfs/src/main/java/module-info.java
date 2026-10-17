import be.imgn.gaia.memfs.internal.MemoryFileSystemProvider;
import java.nio.file.spi.FileSystemProvider;

/**
 * An in-memory file system, for the {@code mem:} URI scheme. See {@link be.imgn.gaia.memfs.MemoryFileSystems}.
 */
module be.imgn.gaia.memfs {
    requires be.imgn.gaia.core;
    requires transitive org.jspecify;

    exports be.imgn.gaia.memfs;

    provides FileSystemProvider with
            MemoryFileSystemProvider;
}
