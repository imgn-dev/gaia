package be.imgn.gaia.platform.testing;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.platform.Directories;
import be.imgn.gaia.platform.Platform;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Platform} with the operating system of your choice and its directories under a root directory, such as a
 * test's temporary directory. Code that receives a {@code Platform} can be tested on any operating system, whatever the
 * one the tests run on, without touching the real directories.
 *
 * <p>The operating system only sets {@link #isLinux()}, {@link #isMacOS()} and {@link #isWindows()}; the directories
 * are {@link FakeDirectories} under the root unless others are given, and the file system is that of the root.
 *
 * <pre>{@code
 * Platform platform = FakePlatform.newBuilder(OperatingSystem.WINDOWS, temporaryDirectory).build();
 * }</pre>
 *
 * <p>To run the real detection of the directories of an operating system instead, give {@link Platform#newBuilder()}
 * the system properties and environment variables of a {@link PlatformPreset}.
 */
public final class FakePlatform implements Platform {

    private final OperatingSystem operatingSystem;
    private final FileSystem fileSystem;
    private final Directories directories;

    private FakePlatform(OperatingSystem operatingSystem, FileSystem fileSystem, Directories directories) {
        this.operatingSystem = operatingSystem;
        this.fileSystem = fileSystem;
        this.directories = directories;
    }

    /**
     * Returns a builder of a platform running an operating system, with its directories under a root directory.
     *
     * @param operatingSystem the operating system of the platform
     * @param root the directory under which the directories of the platform are placed by default, and whose file
     *     system is the platform's; it need not exist
     * @return a new builder
     * @throws IllegalArgumentException if {@code root} is not absolute
     */
    public static Builder newBuilder(OperatingSystem operatingSystem, Path root) {
        return new Builder(operatingSystem, root);
    }

    @Override
    public boolean isLinux() {
        return operatingSystem == OperatingSystem.LINUX;
    }

    @Override
    public boolean isMacOS() {
        return operatingSystem == OperatingSystem.MAC_OS;
    }

    @Override
    public boolean isWindows() {
        return operatingSystem == OperatingSystem.WINDOWS;
    }

    @Override
    public FileSystem fileSystem() {
        return fileSystem;
    }

    @Override
    public Directories directories() {
        return directories;
    }

    /** Builds a {@link FakePlatform}. */
    public static final class Builder {

        private final OperatingSystem operatingSystem;
        private final FileSystem fileSystem;
        private Directories directories;

        private Builder(OperatingSystem operatingSystem, Path root) {
            this.operatingSystem = requireNonNull(operatingSystem, "operatingSystem");
            requireNonNull(root, "root");
            directories = FakeDirectories.newBuilder(root).build();
            fileSystem = root.getFileSystem();
        }

        /**
         * Sets the directories of the platform, instead of the default {@link FakeDirectories} under the root. Like any
         * platform's, they must all be on the platform's file system: that of the root.
         *
         * @param directories the directories
         * @return this builder
         * @throws IllegalArgumentException if a directory is not on the file system of the root
         */
        public Builder directories(Directories directories) {
            requireNonNull(directories, "directories");
            List<Path> all = new ArrayList<>(List.of(
                    directories.home(),
                    directories.dataHome(),
                    directories.configHome(),
                    directories.stateHome(),
                    directories.cacheHome()));
            directories.runtimeDir().ifPresent(all::add);
            all.addAll(directories.dataDirs());
            all.addAll(directories.configDirs());
            for (Path directory : all) {
                checkArgument(
                        directory.getFileSystem().equals(fileSystem),
                        "The directories must be on the file system of the root: %s",
                        directory);
            }
            this.directories = directories;
            return this;
        }

        /**
         * Builds the platform.
         *
         * @return the platform
         */
        public FakePlatform build() {
            return new FakePlatform(operatingSystem, fileSystem, directories);
        }
    }
}
