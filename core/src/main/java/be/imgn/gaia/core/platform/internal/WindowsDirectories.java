package be.imgn.gaia.core.platform.internal;

import be.imgn.gaia.core.platform.Directories;
import be.imgn.gaia.core.system.Environment;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The directories of Windows, computed from the environment variables Windows sets. A variable that is unset or not an
 * absolute path is replaced by its default location. Configuration and data go in the roaming application data, which
 * follows the user across machines; state and cache in the local one.
 */
public final class WindowsDirectories implements Directories {

    // Absolute on Windows, whatever the file system the JVM runs on: a drive followed by a separator, or a network
    // path. A drive alone, as in "C:Users", or a separator alone, as in "\Users", is relative to a current directory.
    private static final Pattern ABSOLUTE = Pattern.compile("[A-Za-z]:[\\\\/]|[\\\\/]{2}[^\\\\/]");
    private static final Pattern DRIVE = Pattern.compile("[A-Za-z]:");

    private static final String APPDATA = "APPDATA";
    private static final String LOCALAPPDATA = "LOCALAPPDATA";
    private static final String PROGRAM_DATA = "ProgramData";
    private static final String SYSTEM_DRIVE = "SystemDrive";

    private final Path home;
    private final Path roamingAppData;
    private final Path localAppData;
    private final Path programData;

    /**
     * Resolves the directories from the environment.
     *
     * @param fileSystem the file system the paths belong to
     * @param userHome the {@code user.home} system property
     * @param environment the environment variables
     */
    public WindowsDirectories(FileSystem fileSystem, String userHome, Environment environment) {
        home = fileSystem.getPath(userHome);
        roamingAppData =
                path(fileSystem, environment, APPDATA, home.resolve("AppData").resolve("Roaming"));
        localAppData = path(
                fileSystem, environment, LOCALAPPDATA, home.resolve("AppData").resolve("Local"));
        String systemDrive = environment
                .get(SYSTEM_DRIVE)
                .filter(drive -> DRIVE.matcher(drive).matches())
                .orElse("C:");
        programData = path(fileSystem, environment, PROGRAM_DATA, fileSystem.getPath(systemDrive + "\\ProgramData"));
    }

    private static Path path(FileSystem fileSystem, Environment environment, String variable, Path fallback) {
        return environment
                .get(variable)
                .filter(value -> ABSOLUTE.matcher(value).lookingAt())
                .map(fileSystem::getPath)
                .orElse(fallback);
    }

    @Override
    public Path home() {
        return home;
    }

    @Override
    public Path dataHome() {
        return roamingAppData;
    }

    @Override
    public Path configHome() {
        return roamingAppData;
    }

    @Override
    public Path stateHome() {
        return localAppData;
    }

    @Override
    public Path cacheHome() {
        return localAppData;
    }

    @Override
    public Optional<Path> runtimeDir() {
        return Optional.empty();
    }

    @Override
    public List<Path> dataDirs() {
        return List.of(programData);
    }

    @Override
    public List<Path> configDirs() {
        return List.of(programData);
    }
}
