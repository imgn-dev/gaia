package be.imgn.gaia.platform.internal;

import be.imgn.gaia.platform.Platform;
import java.nio.file.FileSystem;

/**
 * Linux.
 *
 * @param fileSystem the file system of this platform
 * @param directories the directories of this platform
 */
public record LinuxPlatform(FileSystem fileSystem, XdgDirectories directories) implements Platform {

    @Override
    public boolean isLinux() {
        return true;
    }
}
