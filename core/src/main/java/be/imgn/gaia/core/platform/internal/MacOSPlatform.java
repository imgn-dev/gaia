package be.imgn.gaia.core.platform.internal;

import be.imgn.gaia.core.platform.Platform;
import java.nio.file.FileSystem;

/**
 * macOS.
 *
 * @param fileSystem the file system of this platform
 * @param directories the directories of this platform
 */
public record MacOSPlatform(FileSystem fileSystem, MacOSDirectories directories) implements Platform {

    @Override
    public boolean isMacOS() {
        return true;
    }
}
