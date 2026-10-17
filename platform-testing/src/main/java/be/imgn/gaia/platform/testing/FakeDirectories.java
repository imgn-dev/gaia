package be.imgn.gaia.platform.testing;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.platform.Directories;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * {@link Directories} under one root directory, such as a test's temporary directory, so that code under test never
 * touches the real ones.
 *
 * <p>By default, each directory is a distinct subdirectory of the root, named after its method:
 *
 * <ul>
 *   <li>{@link #home()}: {@code home}
 *   <li>{@link #dataHome()}: {@code data-home}
 *   <li>{@link #configHome()}: {@code config-home}
 *   <li>{@link #stateHome()}: {@code state-home}
 *   <li>{@link #cacheHome()}: {@code cache-home}
 *   <li>{@link #runtimeDir()}: {@code runtime-dir}
 *   <li>{@link #dataDirs()}: one directory, {@code data-dir}
 *   <li>{@link #configDirs()}: one directory, {@code config-dir}
 * </ul>
 *
 * <p>Distinct directories show when code puts a file in the wrong one, and one directory in each search list shows
 * whether it searches the lists at all. Any directory can be replaced, the same one given for several of them as on
 * Windows and macOS, and the runtime directory removed, as on macOS and Windows.
 *
 * <p>Like the real ones, the directories are not created unless asked for with
 * {@link Builder#createDirectories(boolean)}: code that writes in them should create them first.
 *
 * <pre>{@code
 * Directories directories = FakeDirectories.newBuilder(temporaryDirectory).withoutRuntimeDir().build();
 * }</pre>
 */
public final class FakeDirectories implements Directories {

    private final Path home;
    private final Path dataHome;
    private final Path configHome;
    private final Path stateHome;
    private final Path cacheHome;
    private final @Nullable Path runtimeDir;
    private final List<Path> dataDirs;
    private final List<Path> configDirs;

    private FakeDirectories(Builder builder) {
        home = builder.home;
        dataHome = builder.dataHome;
        configHome = builder.configHome;
        stateHome = builder.stateHome;
        cacheHome = builder.cacheHome;
        runtimeDir = builder.runtimeDir;
        dataDirs = builder.dataDirs;
        configDirs = builder.configDirs;
    }

    /**
     * Returns a builder of directories under a root directory. Every path given to the builder must be absolute and
     * belong to the file system of the root, which can be any file system, such as a ZIP file system.
     *
     * @param root the directory under which every directory is placed by default; it need not exist
     * @return a new builder
     * @throws IllegalArgumentException if {@code root} is not absolute
     */
    public static Builder newBuilder(Path root) {
        return new Builder(root);
    }

    @Override
    public Path home() {
        return home;
    }

    @Override
    public Path dataHome() {
        return dataHome;
    }

    @Override
    public Path configHome() {
        return configHome;
    }

    @Override
    public Path stateHome() {
        return stateHome;
    }

    @Override
    public Path cacheHome() {
        return cacheHome;
    }

    @Override
    public Optional<Path> runtimeDir() {
        return Optional.ofNullable(runtimeDir);
    }

    @Override
    public List<Path> dataDirs() {
        return dataDirs;
    }

    @Override
    public List<Path> configDirs() {
        return configDirs;
    }

    /**
     * Builds {@link FakeDirectories}. Each directory defaults to its own subdirectory of the root; each method replaces
     * one with a path that must be absolute and belong to the file system of the root, but need not be under the root.
     */
    public static final class Builder {

        private final Path root;
        private Path home;
        private Path dataHome;
        private Path configHome;
        private Path stateHome;
        private Path cacheHome;
        private @Nullable Path runtimeDir;
        private List<Path> dataDirs;
        private List<Path> configDirs;
        private boolean createDirectories;

        private Builder(Path root) {
            requireNonNull(root, "root");
            checkArgument(root.isAbsolute(), "The root must be an absolute path: %s", root);
            this.root = root;
            home = root.resolve("home");
            dataHome = root.resolve("data-home");
            configHome = root.resolve("config-home");
            stateHome = root.resolve("state-home");
            cacheHome = root.resolve("cache-home");
            runtimeDir = root.resolve("runtime-dir");
            dataDirs = List.of(root.resolve("data-dir"));
            configDirs = List.of(root.resolve("config-dir"));
        }

        /**
         * Sets the user's home directory.
         *
         * @param home the home directory
         * @return this builder
         * @throws IllegalArgumentException if {@code home} is not absolute or not on the file system of the root
         */
        public Builder home(Path home) {
            this.home = checked(home, "home");
            return this;
        }

        /**
         * Sets the directory for user-specific data files.
         *
         * @param dataHome the user data directory
         * @return this builder
         * @throws IllegalArgumentException if {@code dataHome} is not absolute or not on the file system of the root
         */
        public Builder dataHome(Path dataHome) {
            this.dataHome = checked(dataHome, "dataHome");
            return this;
        }

        /**
         * Sets the directory for user-specific configuration files.
         *
         * @param configHome the user configuration directory
         * @return this builder
         * @throws IllegalArgumentException if {@code configHome} is not absolute or not on the file system of the root
         */
        public Builder configHome(Path configHome) {
            this.configHome = checked(configHome, "configHome");
            return this;
        }

        /**
         * Sets the directory for user-specific state.
         *
         * @param stateHome the user state directory
         * @return this builder
         * @throws IllegalArgumentException if {@code stateHome} is not absolute or not on the file system of the root
         */
        public Builder stateHome(Path stateHome) {
            this.stateHome = checked(stateHome, "stateHome");
            return this;
        }

        /**
         * Sets the directory for user-specific cached data.
         *
         * @param cacheHome the user cache directory
         * @return this builder
         * @throws IllegalArgumentException if {@code cacheHome} is not absolute or not on the file system of the root
         */
        public Builder cacheHome(Path cacheHome) {
            this.cacheHome = checked(cacheHome, "cacheHome");
            return this;
        }

        /**
         * Sets the directory for user-specific runtime files, replacing any earlier {@link #withoutRuntimeDir()}.
         *
         * @param runtimeDir the user runtime directory
         * @return this builder
         * @throws IllegalArgumentException if {@code runtimeDir} is not absolute or not on the file system of the root
         */
        public Builder runtimeDir(Path runtimeDir) {
            this.runtimeDir = checked(runtimeDir, "runtimeDir");
            return this;
        }

        /**
         * Removes the runtime directory, so that {@link FakeDirectories#runtimeDir()} is empty, as on macOS, Windows,
         * and Linux without {@code XDG_RUNTIME_DIR}.
         *
         * @return this builder
         */
        public Builder withoutRuntimeDir() {
            runtimeDir = null;
            return this;
        }

        /**
         * Sets the system-wide directories to search for data files.
         *
         * @param dataDirs the system data directories, most important first; copied, and may be empty
         * @return this builder
         * @throws IllegalArgumentException if a directory is not absolute or not on the file system of the root
         */
        public Builder dataDirs(List<Path> dataDirs) {
            this.dataDirs = checked(dataDirs, "dataDirs");
            return this;
        }

        /**
         * Sets the system-wide directories to search for configuration files.
         *
         * @param configDirs the system configuration directories, most important first; copied, and may be empty
         * @return this builder
         * @throws IllegalArgumentException if a directory is not absolute or not on the file system of the root
         */
        public Builder configDirs(List<Path> configDirs) {
            this.configDirs = checked(configDirs, "configDirs");
            return this;
        }

        /**
         * Sets whether {@link #build()} creates every directory, with its parents, if it does not exist yet. By default
         * it does not, like the real directories, which may not exist.
         *
         * @param createDirectories {@code true} to create the directories
         * @return this builder
         */
        public Builder createDirectories(boolean createDirectories) {
            this.createDirectories = createDirectories;
            return this;
        }

        /**
         * Builds the directories, creating them if this builder was told to.
         *
         * @return the directories
         * @throws UncheckedIOException if a directory must be created and cannot be, for example because a file has its
         *     name
         */
        public FakeDirectories build() {
            FakeDirectories directories = new FakeDirectories(this);
            if (createDirectories) {
                List<Path> all = new ArrayList<>(List.of(home, dataHome, configHome, stateHome, cacheHome));
                directories.runtimeDir().ifPresent(all::add);
                all.addAll(dataDirs);
                all.addAll(configDirs);
                for (Path directory : all) {
                    try {
                        Files.createDirectories(directory);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Cannot create " + directory, e);
                    }
                }
            }
            return directories;
        }

        private Path checked(Path path, String name) {
            requireNonNull(path, name);
            checkArgument(path.isAbsolute(), "%s must be an absolute path: %s", name, path);
            checkArgument(
                    path.getFileSystem().equals(root.getFileSystem()),
                    "%s must be on the file system of the root %s: %s",
                    name,
                    root,
                    path);
            return path;
        }

        private List<Path> checked(List<Path> paths, String name) {
            requireNonNull(paths, name);
            List<Path> copy = new ArrayList<>(paths.size());
            for (Path path : paths) {
                copy.add(checked(path, name + "[" + copy.size() + "]"));
            }
            return List.copyOf(copy);
        }
    }
}
