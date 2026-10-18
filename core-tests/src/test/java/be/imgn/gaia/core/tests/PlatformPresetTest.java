package be.imgn.gaia.core.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.platform.Directories;
import be.imgn.gaia.core.platform.Platform;
import be.imgn.gaia.core.testing.OperatingSystem;
import be.imgn.gaia.core.testing.PlatformPreset;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class PlatformPresetTest {

    private static final FileSystem DEFAULT = FileSystems.getDefault();

    private static Platform detect(PlatformPreset preset, FileSystem fileSystem) {
        return Platform.newBuilder()
                .systemProperties(preset.systemProperties())
                .environment(preset.environment())
                .fileSystem(fileSystem)
                .detect();
    }

    private static Platform detect(PlatformPreset.Builder preset) {
        return detect(preset.build(), DEFAULT);
    }

    private static Path path(String path) {
        return DEFAULT.getPath(path);
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
        "LINUX,      true,  false, false",
        "MAC_OS,     false, true,  false",
        "WINDOWS,    false, false, true",
        "OTHER_UNIX, false, false, false",
    })
    void detectsTheOperatingSystemOfThePreset(
            OperatingSystem operatingSystem, boolean linux, boolean macOS, boolean windows) {
        Platform platform = detect(PlatformPreset.newBuilder(operatingSystem));

        assertThat(platform.isLinux()).isEqualTo(linux);
        assertThat(platform.isMacOS()).isEqualTo(macOS);
        assertThat(platform.isWindows()).isEqualTo(windows);
    }

    @ParameterizedTest
    @EnumSource(
            value = OperatingSystem.class,
            names = {"LINUX", "OTHER_UNIX"})
    void detectsTheXdgDefaultsUnderTheHomeOfTheTestUser(OperatingSystem operatingSystem) {
        Directories directories =
                detect(PlatformPreset.newBuilder(operatingSystem)).directories();

        assertThat(directories.home()).isEqualTo(path("/home/test"));
        assertThat(directories.dataHome()).isEqualTo(path("/home/test/.local/share"));
        assertThat(directories.configHome()).isEqualTo(path("/home/test/.config"));
        assertThat(directories.stateHome()).isEqualTo(path("/home/test/.local/state"));
        assertThat(directories.cacheHome()).isEqualTo(path("/home/test/.cache"));
        assertThat(directories.runtimeDir()).isEmpty();
        assertThat(directories.dataDirs()).containsExactly(path("/usr/local/share"), path("/usr/share"));
        assertThat(directories.configDirs()).containsExactly(path("/etc/xdg"));
    }

    @ParameterizedTest
    @EnumSource(
            value = OperatingSystem.class,
            names = {"LINUX", "OTHER_UNIX"})
    void detectsTheXdgVariablesWhenSet(OperatingSystem operatingSystem) {
        Directories directories = detect(
                        PlatformPreset.newBuilder(operatingSystem).xdgVariables(true))
                .directories();

        assertThat(directories.home()).isEqualTo(path("/home/test"));
        assertThat(directories.dataHome()).isEqualTo(path("/xdg/data-home"));
        assertThat(directories.configHome()).isEqualTo(path("/xdg/config-home"));
        assertThat(directories.stateHome()).isEqualTo(path("/xdg/state-home"));
        assertThat(directories.cacheHome()).isEqualTo(path("/xdg/cache-home"));
        assertThat(directories.runtimeDir()).contains(path("/xdg/runtime-dir"));
        assertThat(directories.dataDirs()).containsExactly(path("/xdg/data-dir/first"), path("/xdg/data-dir/second"));
        assertThat(directories.configDirs())
                .containsExactly(path("/xdg/config-dir/first"), path("/xdg/config-dir/second"));
    }

    @Test
    void setsEveryXdgVariableToALocationOtherThanItsDefault() {
        Directories defaults =
                detect(PlatformPreset.newBuilder(OperatingSystem.LINUX)).directories();
        Directories xdg = detect(
                        PlatformPreset.newBuilder(OperatingSystem.LINUX).xdgVariables(true))
                .directories();

        assertThat(xdg.dataHome()).isNotEqualTo(defaults.dataHome());
        assertThat(xdg.configHome()).isNotEqualTo(defaults.configHome());
        assertThat(xdg.stateHome()).isNotEqualTo(defaults.stateHome());
        assertThat(xdg.cacheHome()).isNotEqualTo(defaults.cacheHome());
        assertThat(xdg.runtimeDir()).isNotEqualTo(defaults.runtimeDir());
        assertThat(xdg.dataDirs()).doesNotContainAnyElementsOf(defaults.dataDirs());
        assertThat(xdg.configDirs()).doesNotContainAnyElementsOf(defaults.configDirs());
        assertThat(allPaths(xdg)).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void detectsTheStandardLocationsOfMacOSWhateverTheXdgVariables(boolean xdgVariables) {
        Directories directories = detect(
                        PlatformPreset.newBuilder(OperatingSystem.MAC_OS).xdgVariables(xdgVariables))
                .directories();

        assertThat(directories.home()).isEqualTo(path("/Users/test"));
        assertThat(directories.dataHome()).isEqualTo(path("/Users/test/Library/Application Support"));
        assertThat(directories.configHome()).isEqualTo(path("/Users/test/Library/Application Support"));
        assertThat(directories.stateHome()).isEqualTo(path("/Users/test/Library/Application Support"));
        assertThat(directories.cacheHome()).isEqualTo(path("/Users/test/Library/Caches"));
        assertThat(directories.runtimeDir()).isEmpty();
        assertThat(directories.dataDirs()).containsExactly(path("/Library/Application Support"));
        assertThat(directories.configDirs()).containsExactly(path("/Library/Application Support"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void detectsTheWindowsDirectoriesFromTheVariablesWhateverTheXdgVariables(boolean xdgVariables) {
        Directories directories = detect(
                        PlatformPreset.newBuilder(OperatingSystem.WINDOWS).xdgVariables(xdgVariables))
                .directories();

        assertThat(directories.home()).isEqualTo(path("C:\\Users\\test"));
        assertThat(directories.dataHome()).isEqualTo(path("C:\\Users\\test\\AppData\\Roaming"));
        assertThat(directories.configHome()).isEqualTo(path("C:\\Users\\test\\AppData\\Roaming"));
        assertThat(directories.stateHome()).isEqualTo(path("C:\\Users\\test\\AppData\\Local"));
        assertThat(directories.cacheHome()).isEqualTo(path("C:\\Users\\test\\AppData\\Local"));
        assertThat(directories.runtimeDir()).isEmpty();
        assertThat(directories.dataDirs()).containsExactly(path("C:\\ProgramData"));
        assertThat(directories.configDirs()).containsExactly(path("C:\\ProgramData"));
    }

    @Test
    void detectsTheWindowsDefaultsWhenTheVariablesAreRemoved() {
        Directories directories = detect(PlatformPreset.newBuilder(OperatingSystem.WINDOWS)
                        .removeEnvironmentVariable("APPDATA")
                        .removeEnvironmentVariable("LOCALAPPDATA")
                        .removeEnvironmentVariable("ProgramData")
                        .environmentVariable("SystemDrive", "E:"))
                .directories();
        Path home = path("C:\\Users\\test");

        assertThat(directories.configHome()).isEqualTo(home.resolve("AppData").resolve("Roaming"));
        assertThat(directories.cacheHome()).isEqualTo(home.resolve("AppData").resolve("Local"));
        assertThat(directories.dataDirs()).containsExactly(path("E:\\ProgramData"));
    }

    @Test
    void detectsTheHomeFromTheHomeVariableBeforeTheUserHomeProperty() {
        PlatformPreset.Builder preset = PlatformPreset.newBuilder(OperatingSystem.LINUX)
                .home("/home/alice")
                .environmentVariable("HOME", "/users/bob");

        assertThat(detect(preset).directories().home()).isEqualTo(path("/users/bob"));
        assertThat(detect(preset.removeEnvironmentVariable("HOME"))
                        .directories()
                        .home())
                .isEqualTo(path("/home/alice"));
    }

    @ParameterizedTest
    @CsvSource({
        "LINUX,      /home/alice",
        "OTHER_UNIX, /home/alice",
        "MAC_OS,     /Users/alice",
        "WINDOWS,    C:\\Users\\alice",
    })
    void derivesTheHomeFromTheUserName(OperatingSystem operatingSystem, String home) {
        PlatformPreset preset =
                PlatformPreset.newBuilder(operatingSystem).userName("alice").build();

        assertThat(preset.systemProperties().userName()).isEqualTo("alice");
        assertThat(preset.systemProperties().userHome()).isEqualTo(home);
        assertThat(preset.systemProperties().userDir()).isEqualTo(home);
        assertThat(detect(preset, DEFAULT).directories().home()).isEqualTo(path(home));
    }

    @ParameterizedTest
    @CsvSource({
        "LINUX,      /srv/alice",
        "OTHER_UNIX, /usr/home/alice",
        "MAC_OS,     /Volumes/Data/alice",
        "WINDOWS,    D:\\Profiles\\alice",
    })
    void keepsTheGivenHomeWhateverTheUserName(OperatingSystem operatingSystem, String home) {
        PlatformPreset preset = PlatformPreset.newBuilder(operatingSystem)
                .home(home)
                .userName("bob")
                .build();

        assertThat(preset.systemProperties().userName()).isEqualTo("bob");
        assertThat(preset.systemProperties().userHome()).isEqualTo(home);
        assertThat(detect(preset, DEFAULT).directories().home()).isEqualTo(path(home));
    }

    @Test
    void placesTheWindowsApplicationDataUnderTheGivenHome() {
        PlatformPreset preset = PlatformPreset.newBuilder(OperatingSystem.WINDOWS)
                .home("D:\\Profiles\\alice")
                .build();

        assertThat(preset.environment().get("USERPROFILE")).contains("D:\\Profiles\\alice");
        assertThat(preset.environment().get("APPDATA")).contains("D:\\Profiles\\alice\\AppData\\Roaming");
        assertThat(preset.environment().get("LOCALAPPDATA")).contains("D:\\Profiles\\alice\\AppData\\Local");
        assertThat(preset.environment().get("TEMP")).contains("D:\\Profiles\\alice\\AppData\\Local\\Temp");
        assertThat(preset.systemProperties().javaIoTmpdir()).isEqualTo("D:\\Profiles\\alice\\AppData\\Local\\Temp\\");
        assertThat(detect(preset, DEFAULT).directories().cacheHome())
                .isEqualTo(path("D:\\Profiles\\alice\\AppData\\Local"));
    }

    @ParameterizedTest
    @CsvSource({
        "LINUX,      Linux,      /,  :, /tmp",
        "OTHER_UNIX, FreeBSD,    /,  :, /tmp",
        "MAC_OS,     Mac OS X,   /,  :, /tmp",
        "WINDOWS,    Windows 11, \\, ;, C:\\Users\\test\\AppData\\Local\\Temp\\",
    })
    void setsTheTypicalSystemProperties(
            OperatingSystem operatingSystem,
            String osName,
            String fileSeparator,
            String pathSeparator,
            String temporaryDirectory) {
        PlatformPreset preset = PlatformPreset.newBuilder(operatingSystem).build();

        assertThat(preset.systemProperties().osName()).isEqualTo(osName);
        assertThat(preset.systemProperties().osVersion()).isNotBlank();
        assertThat(preset.systemProperties().osArch()).isNotBlank();
        assertThat(preset.systemProperties().fileSeparator()).isEqualTo(fileSeparator);
        assertThat(preset.systemProperties().pathSeparator()).isEqualTo(pathSeparator);
        assertThat(preset.systemProperties().lineSeparator())
                .isEqualTo(operatingSystem == OperatingSystem.WINDOWS ? "\r\n" : "\n");
        assertThat(preset.systemProperties().javaIoTmpdir()).isEqualTo(temporaryDirectory);
        assertThat(preset.systemProperties().userName()).isEqualTo("test");
    }

    @ParameterizedTest
    @EnumSource(
            value = OperatingSystem.class,
            names = {"LINUX", "OTHER_UNIX", "MAC_OS"})
    void setsHomeAndUserOnUnixLikeSystems(OperatingSystem operatingSystem) {
        PlatformPreset preset = PlatformPreset.newBuilder(operatingSystem).build();

        assertThat(preset.environment().home())
                .contains(preset.systemProperties().userHome());
        assertThat(preset.environment().get("USER")).contains("test");
        assertThat(preset.environment().get("USERPROFILE")).isEmpty();
        assertThat(preset.environment().get("XDG_CONFIG_HOME")).isEmpty();
    }

    @Test
    void setsTheVariablesWindowsSets() {
        PlatformPreset preset =
                PlatformPreset.newBuilder(OperatingSystem.WINDOWS).build();

        assertThat(preset.environment().home()).isEmpty();
        assertThat(preset.environment().get("USERNAME")).contains("test");
        assertThat(preset.environment().get("USERPROFILE")).contains("C:\\Users\\test");
        assertThat(preset.environment().get("APPDATA")).contains("C:\\Users\\test\\AppData\\Roaming");
        assertThat(preset.environment().get("LOCALAPPDATA")).contains("C:\\Users\\test\\AppData\\Local");
        assertThat(preset.environment().get("TEMP")).contains("C:\\Users\\test\\AppData\\Local\\Temp");
        assertThat(preset.environment().get("TMP")).contains("C:\\Users\\test\\AppData\\Local\\Temp");
        assertThat(preset.environment().get("ProgramData")).contains("C:\\ProgramData");
        assertThat(preset.environment().get("SystemDrive")).contains("C:");
        assertThat(preset.environment().get("SystemRoot")).contains("C:\\Windows");
        assertThat(preset.environment().get("programdata")).isEmpty();
    }

    @Test
    void appliesTheGivenSystemPropertiesOverThePreset() {
        PlatformPreset.Builder preset = PlatformPreset.newBuilder(OperatingSystem.MAC_OS)
                .systemProperty("os.name", "Linux")
                .systemProperty("custom", "value");

        assertThat(preset.build().systemProperties().get("custom")).contains("value");
        assertThat(detect(preset).isLinux()).isTrue();
        assertThat(detect(preset).directories().home()).isEqualTo(path("/Users/test"));
    }

    @Test
    void appliesTheGivenEnvironmentVariablesOverThePreset() {
        PlatformPreset.Builder preset = PlatformPreset.newBuilder(OperatingSystem.LINUX)
                .xdgVariables(true)
                .environmentVariable("XDG_CONFIG_HOME", "/custom/config")
                .environmentVariable("CUSTOM", "value");

        assertThat(preset.build().environment().get("CUSTOM")).contains("value");
        assertThat(detect(preset).directories().configHome()).isEqualTo(path("/custom/config"));
        assertThat(detect(preset).directories().dataHome()).isEqualTo(path("/xdg/data-home"));
    }

    @Test
    void removesAVariableOfThePresetOrSetEarlier() {
        PlatformPreset preset = PlatformPreset.newBuilder(OperatingSystem.LINUX)
                .xdgVariables(true)
                .environmentVariable("CUSTOM", "value")
                .removeEnvironmentVariable("CUSTOM")
                .removeEnvironmentVariable("USER")
                .removeEnvironmentVariable("XDG_RUNTIME_DIR")
                .removeEnvironmentVariable("NEVER_SET")
                .build();

        assertThat(preset.environment().get("CUSTOM")).isEmpty();
        assertThat(preset.environment().get("USER")).isEmpty();
        assertThat(preset.environment().get("NEVER_SET")).isEmpty();
        assertThat(detect(preset, DEFAULT).directories().runtimeDir()).isEmpty();
        assertThat(detect(preset, DEFAULT).directories().cacheHome()).isEqualTo(path("/xdg/cache-home"));
    }

    @Test
    void setsAVariableAgainAfterRemovingIt() {
        PlatformPreset preset = PlatformPreset.newBuilder(OperatingSystem.LINUX)
                .removeEnvironmentVariable("HOME")
                .environmentVariable("HOME", "/users/bob")
                .build();

        assertThat(preset.environment().home()).contains("/users/bob");
    }

    @Test
    void buildsIndependentPresetsFromTheSameBuilder() {
        PlatformPreset.Builder builder = PlatformPreset.newBuilder(OperatingSystem.LINUX);
        PlatformPreset first = builder.build();

        PlatformPreset second =
                builder.userName("alice").environmentVariable("CUSTOM", "value").build();

        assertThat(first.systemProperties().userHome()).isEqualTo("/home/test");
        assertThat(first.environment().get("CUSTOM")).isEmpty();
        assertThat(second.systemProperties().userHome()).isEqualTo("/home/alice");
        assertThat(second.environment().get("CUSTOM")).contains("value");
    }

    @ParameterizedTest
    @EnumSource(OperatingSystem.class)
    void detectsEveryPathOnTheGivenFileSystem(OperatingSystem operatingSystem, @TempDir Path temporary)
            throws IOException {
        PlatformPreset preset =
                PlatformPreset.newBuilder(operatingSystem).xdgVariables(true).build();
        try (FileSystem zip = FileSystems.newFileSystem(temporary.resolve("platform.zip"), Map.of("create", "true"))) {
            Platform platform = detect(preset, zip);

            assertThat(platform.fileSystem()).isSameAs(zip);
            assertThat(platform.directories().home())
                    .isEqualTo(zip.getPath(preset.systemProperties().userHome()));
            assertThat(allPaths(platform.directories()))
                    .isNotEmpty()
                    .allSatisfy(path -> assertThat(path.getFileSystem()).isSameAs(zip));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsABlankUserNameOrHome(String blank) {
        PlatformPreset.Builder builder = PlatformPreset.newBuilder(OperatingSystem.LINUX);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.userName(blank))
                .withMessageContaining("userName");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.home(blank))
                .withMessageContaining("home");
        assertThat(builder.build().systemProperties().userHome()).isEqualTo("/home/test");
    }

    @Test
    void rejectsAnEmptySystemPropertyName() {
        PlatformPreset.Builder builder = PlatformPreset.newBuilder(OperatingSystem.LINUX);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.systemProperty("", "value"))
                .withMessageContaining("empty");
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullArguments() {
        PlatformPreset.Builder builder = PlatformPreset.newBuilder(OperatingSystem.LINUX);

        assertThatNullPointerException().isThrownBy(() -> PlatformPreset.newBuilder(null));
        assertThatNullPointerException().isThrownBy(() -> builder.userName(null));
        assertThatNullPointerException().isThrownBy(() -> builder.home(null));
        assertThatNullPointerException().isThrownBy(() -> builder.systemProperty(null, "value"));
        assertThatNullPointerException().isThrownBy(() -> builder.systemProperty("name", null));
        assertThatNullPointerException().isThrownBy(() -> builder.environmentVariable(null, "value"));
        assertThatNullPointerException().isThrownBy(() -> builder.environmentVariable("name", null));
        assertThatNullPointerException().isThrownBy(() -> builder.removeEnvironmentVariable(null));
    }
}
