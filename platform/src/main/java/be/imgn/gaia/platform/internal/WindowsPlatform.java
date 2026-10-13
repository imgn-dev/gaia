package be.imgn.gaia.platform.internal;

import be.imgn.gaia.platform.Platform;
import java.nio.file.FileSystem;

/**
 * Windows.
 *
 * @param fileSystem the file system of this platform
 * @param directories the directories of this platform
 */
public record WindowsPlatform(FileSystem fileSystem, WindowsDirectories directories) implements Platform {

    @Override
    public boolean isWindows() {
        return true;
    }
}
