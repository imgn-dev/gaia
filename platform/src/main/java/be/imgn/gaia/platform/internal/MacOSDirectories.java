package be.imgn.gaia.platform.internal;

import be.imgn.gaia.platform.Directories;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The standard directories of macOS, under {@code ~/Library} and {@code /Library}. The environment is not consulted.
 */
public final class MacOSDirectories implements Directories {

    private final Path home;
    private final Path applicationSupport;
    private final Path caches;
    private final Path systemApplicationSupport;

    /**
     * Resolves the directories under the user's home.
     *
     * @param fileSystem the file system the paths belong to
     * @param userHome the {@code user.home} system property
     */
    public MacOSDirectories(FileSystem fileSystem, String userHome) {
        home = fileSystem.getPath(userHome);
        applicationSupport = home.resolve("Library").resolve("Application Support");
        caches = home.resolve("Library").resolve("Caches");
        systemApplicationSupport = fileSystem.getPath("/Library/Application Support");
    }

    @Override
    public Path home() {
        return home;
    }

    @Override
    public Path dataHome() {
        return applicationSupport;
    }

    @Override
    public Path configHome() {
        return applicationSupport;
    }

    @Override
    public Path stateHome() {
        return applicationSupport;
    }

    @Override
    public Path cacheHome() {
        return caches;
    }

    @Override
    public Optional<Path> runtimeDir() {
        return Optional.empty();
    }

    @Override
    public List<Path> dataDirs() {
        return List.of(systemApplicationSupport);
    }

    @Override
    public List<Path> configDirs() {
        return List.of(systemApplicationSupport);
    }
}
