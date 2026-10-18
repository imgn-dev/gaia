package be.imgn.gaia.core.platform.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.imgn.gaia.core.platform.Directories;
import be.imgn.gaia.core.platform.Platform;
import be.imgn.gaia.core.system.Environment;
import be.imgn.gaia.core.system.SystemProperties;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PlatformsTest {

    private static final Environment NO_VARIABLES = Environment.of(Map.of());

    private static Platform detect(String osName, Environment environment, FileSystem fileSystem) {
        return Platform.newBuilder()
                .systemProperties(SystemProperties.of(Map.of("os.name", osName, "user.home", "/home/alice")))
                .environment(environment)
                .fileSystem(fileSystem)
                .detect();
    }

    private static Platform detect(String osName, Environment environment) {
        return detect(osName, environment, FileSystems.getDefault());
    }

    private static List<Path> allPaths(Directories directories) {
        List<Path> paths = new ArrayList<>(List.of(
                directories.home(),
                directories.dataHome(),
                directories.configHome(),
                directories.stateHome(),
                directories.cacheHome()));
        directories.runtimeDir().ifPresent(paths::add);
        paths.addAll(directories.dataDirs());
        paths.addAll(directories.configDirs());
        return paths;
    }

    @ParameterizedTest
    @CsvSource({
        "Linux,               true,  false, false",
        "Mac OS X,            false, true,  false",
        "Darwin,              false, true,  false",
        "Windows 11,          false, false, true",
        "Windows 10,          false, false, true",
        "Windows Server 2025, false, false, true",
        "FreeBSD,             false, false, false",
        "SunOS,               false, false, false",
    })
    void detectsTheOperatingSystemFromOsName(String osName, boolean linux, boolean macOS, boolean windows) {
        Platform platform = detect(osName, NO_VARIABLES);

        assertThat(platform.isLinux()).isEqualTo(linux);
        assertThat(platform.isMacOS()).isEqualTo(macOS);
        assertThat(platform.isWindows()).isEqualTo(windows);
    }

    @ParameterizedTest
    @ValueSource(strings = {"os.name", "user.home"})
    void requiresOsNameAndUserHome(String missing) {
        Map<String, String> properties = new HashMap<>(Map.of("os.name", "Linux", "user.home", "/home/alice"));
        properties.remove(missing);

        Platform.Builder builder = Platform.newBuilder().systemProperties(SystemProperties.of(properties));

        assertThatThrownBy(builder::detect)
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining(missing);
    }

    @ParameterizedTest
    @CsvSource({
        "Linux,      LinuxPlatform,   XdgDirectories",
        "FreeBSD,    UnixPlatform,    XdgDirectories",
        "Mac OS X,   MacOSPlatform,   MacOSDirectories",
        "Windows 11, WindowsPlatform, WindowsDirectories",
    })
    void pairsEachPlatformWithItsDirectories(String osName, String platform, String directories) {
        Platform detected = detect(osName, NO_VARIABLES);

        assertThat(detected.getClass().getSimpleName()).isEqualTo(platform);
        assertThat(detected.directories().getClass().getSimpleName()).isEqualTo(directories);
    }

    @Test
    void readsTheDirectoriesFromTheGivenSources() {
        Environment environment = Environment.of(Map.of("XDG_CONFIG_HOME", "/xdg/config", "APPDATA", "C:\\Roaming"));

        assertThat(detect("Linux", environment).directories().home()).isEqualTo(Path.of("/home/alice"));
        assertThat(detect("Linux", environment).directories().configHome()).isEqualTo(Path.of("/xdg/config"));
        assertThat(detect("Windows 11", environment).directories().configHome()).isEqualTo(Path.of("C:\\Roaming"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Linux", "FreeBSD", "Mac OS X", "Windows 11"})
    void buildsEveryPathOnTheGivenFileSystem(String osName, @TempDir Path temporary) throws IOException {
        Environment environment = Environment.of(Map.of("XDG_RUNTIME_DIR", "/run/user/1000", "APPDATA", "C:\\Roaming"));
        try (FileSystem zip = FileSystems.newFileSystem(temporary.resolve("platform.zip"), Map.of("create", "true"))) {
            Platform platform = detect(osName, environment, zip);

            assertThat(platform.fileSystem()).isSameAs(zip);
            assertThat(allPaths(platform.directories()))
                    .isNotEmpty()
                    .allSatisfy(path -> assertThat(path.getFileSystem()).isSameAs(zip));
        }
    }

    @Test
    void defaultsEverySourceToTheRunningJvm() {
        Platform detected = Platform.newBuilder().detect();
        Platform current = Platform.current();

        assertThat(detected).isNotSameAs(current).hasSameClassAs(current);
        assertThat(detected.fileSystem()).isSameAs(FileSystems.getDefault());
        assertThat(detected.directories().home())
                .isEqualTo(current.directories().home());
    }

    @Test
    void defaultsOnlyTheSourcesNotGiven(@TempDir Path temporary) throws IOException {
        try (FileSystem zip = FileSystems.newFileSystem(temporary.resolve("platform.zip"), Map.of("create", "true"))) {
            Platform platform = Platform.newBuilder().fileSystem(zip).detect();

            assertThat(platform.fileSystem()).isSameAs(zip);
            assertThat(platform.getClass()).isEqualTo(Platform.current().getClass());
            assertThat(platform.directories().home())
                    .hasToString(Platform.current().directories().home().toString());
        }
    }

    @Test
    void currentIsDetectedOnceOnTheDefaultFileSystem() {
        Platform current = Platform.current();

        assertThat(Platform.current()).isSameAs(current);
        assertThat(current.fileSystem()).isSameAs(FileSystems.getDefault());
        assertThat(Stream.of(current.isLinux(), current.isMacOS(), current.isWindows())
                        .filter(Boolean::booleanValue)
                        .count())
                .isLessThanOrEqualTo(1);
    }
}
