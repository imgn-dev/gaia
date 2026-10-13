package be.imgn.gaia.platform.internal;

import be.imgn.gaia.core.system.Environment;
import be.imgn.gaia.core.text.Splitter;
import be.imgn.gaia.platform.Directories;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The directories of the XDG Base Directory Specification, used on Linux and other Unix-like systems. A variable that
 * is unset, empty or not an absolute path is ignored, and the specification's default used instead.
 */
public final class XdgDirectories implements Directories {

    private static final Splitter SEPARATOR = Splitter.on(':');

    private static final String XDG_DATA_HOME = "XDG_DATA_HOME";
    private static final String XDG_CONFIG_HOME = "XDG_CONFIG_HOME";
    private static final String XDG_STATE_HOME = "XDG_STATE_HOME";
    private static final String XDG_CACHE_HOME = "XDG_CACHE_HOME";
    private static final String XDG_RUNTIME_DIR = "XDG_RUNTIME_DIR";
    private static final String XDG_DATA_DIRS = "XDG_DATA_DIRS";
    private static final String XDG_CONFIG_DIRS = "XDG_CONFIG_DIRS";

    private final Path home;
    private final Path dataHome;
    private final Path configHome;
    private final Path stateHome;
    private final Path cacheHome;
    private final @Nullable Path runtimeDir;
    private final List<Path> dataDirs;
    private final List<Path> configDirs;

    /**
     * Resolves the directories from the environment.
     *
     * @param fileSystem the file system the paths belong to
     * @param userHome the {@code user.home} system property, used if {@code HOME} is unusable
     * @param environment the environment variables
     */
    public XdgDirectories(FileSystem fileSystem, String userHome, Environment environment) {
        home = environment
                .home()
                .filter(XdgDirectories::isAbsolute)
                .map(fileSystem::getPath)
                .orElse(fileSystem.getPath(userHome));
        dataHome = absolutePath(fileSystem, environment, XDG_DATA_HOME)
                .orElse(home.resolve(".local").resolve("share"));
        configHome = absolutePath(fileSystem, environment, XDG_CONFIG_HOME).orElse(home.resolve(".config"));
        stateHome = absolutePath(fileSystem, environment, XDG_STATE_HOME)
                .orElse(home.resolve(".local").resolve("state"));
        cacheHome = absolutePath(fileSystem, environment, XDG_CACHE_HOME).orElse(home.resolve(".cache"));
        runtimeDir = absolutePath(fileSystem, environment, XDG_RUNTIME_DIR).orElse(null);
        dataDirs = absolutePaths(
                fileSystem,
                environment,
                XDG_DATA_DIRS,
                List.of(fileSystem.getPath("/usr/local/share"), fileSystem.getPath("/usr/share")));
        configDirs = absolutePaths(fileSystem, environment, XDG_CONFIG_DIRS, List.of(fileSystem.getPath("/etc/xdg")));
    }

    private static Optional<Path> absolutePath(FileSystem fileSystem, Environment environment, String variable) {
        return environment.get(variable).filter(XdgDirectories::isAbsolute).map(fileSystem::getPath);
    }

    private static List<Path> absolutePaths(
            FileSystem fileSystem, Environment environment, String variable, List<Path> fallback) {
        List<Path> paths = environment.get(variable).stream()
                .flatMap(SEPARATOR::splitToStream)
                .filter(XdgDirectories::isAbsolute)
                .map(fileSystem::getPath)
                .toList();
        return paths.isEmpty() ? fallback : paths;
    }

    // XDG is a POSIX specification: a path is absolute when it starts with a slash, whatever the file system the
    // JVM runs on.
    private static boolean isAbsolute(String path) {
        return path.startsWith("/");
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
}
