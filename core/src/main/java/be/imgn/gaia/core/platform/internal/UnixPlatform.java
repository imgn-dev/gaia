package be.imgn.gaia.core.platform.internal;

import be.imgn.gaia.core.platform.Platform;
import java.nio.file.FileSystem;

/**
 * A Unix-like system other than Linux and macOS, such as FreeBSD. All the {@code is*()} methods return {@code false}.
 *
 * @param fileSystem the file system of this platform
 * @param directories the directories of this platform
 */
public record UnixPlatform(FileSystem fileSystem, XdgDirectories directories) implements Platform {}
