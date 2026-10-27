package be.imgn.gaia.core.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.gaia.core.platform.Platform;
import be.imgn.gaia.core.testing.FakePlatform;
import be.imgn.gaia.core.testing.OperatingSystem;
import be.imgn.gaia.core.testing.PlatformPreset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationDirectoriesTest {

    private static final FileSystem DEFAULT = FileSystems.getDefault();

    @TempDir
    Path root;

    private static Platform detect(OperatingSystem operatingSystem) {
        return detect(PlatformPreset.newBuilder(operatingSystem));
    }

    private static Platform detect(PlatformPreset.Builder builder) {
        PlatformPreset preset = builder.build();
        return Platform.newBuilder()
                .systemProperties(preset.systemProperties())
                .environment(preset.environment())
                .fileSystem(DEFAULT)
                .detect();
    }

    private static Path path(String path) {
        return DEFAULT.getPath(path);
    }

    private static List<Path> allPaths(ApplicationDirectories directories) {
        return List.of(directories.config(), directories.data(), directories.state(), directories.cache());
    }

    @Test
    void followsTheXdgBaseDirectoriesOnLinux() {
        ApplicationDirectories directories = detect(PlatformPreset.newBuilder(OperatingSystem.LINUX)
                        .environmentVariable("XDG_RUNTIME_DIR", "/run/user/1000"))
                .applicationDirectories("Quuz");

        assertThat(directories.config()).isEqualTo(path("/home/test/.config/quuz"));
        assertThat(directories.data()).isEqualTo(path("/home/test/.local/share/quuz"));
        assertThat(directories.state()).isEqualTo(path("/home/test/.local/state/quuz"));
        assertThat(directories.cache()).isEqualTo(path("/home/test/.cache/quuz"));
        assertThat(directories.runtime()).contains(path("/run/user/1000/quuz"));
    }

    @Test
    void hasNoRuntimeDirectoryWithoutTheXdgOne() {
        ApplicationDirectories directories = detect(
                        PlatformPreset.newBuilder(OperatingSystem.LINUX).removeEnvironmentVariable("XDG_RUNTIME_DIR"))
                .applicationDirectories("Quuz");

        assertThat(directories.runtime()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = OperatingSystem.class,
            names = {"LINUX", "OTHER_UNIX"})
    void namesTheXdgDirectoriesInLowerCaseWithoutSpacesWhateverTheVendorAndQualifier(OperatingSystem operatingSystem) {
        Platform platform = detect(operatingSystem);

        assertThat(platform.applicationDirectories("foo", "bar", "Quuz Pro").config())
                .isEqualTo(path("/home/test/.config/quuz-pro"));
        assertThat(platform.applicationDirectories("bar", "Quuz Pro").config())
                .isEqualTo(path("/home/test/.config/quuz-pro"));
    }

    @Test
    void keepsEachKindApartUnderApplicationSupportOnMacOS() {
        ApplicationDirectories directories = detect(OperatingSystem.MAC_OS).applicationDirectories("Quuz");

        Path applicationSupport = path("/Users/test/Library/Application Support/Quuz");
        assertThat(directories.config()).isEqualTo(applicationSupport.resolve("config"));
        assertThat(directories.data()).isEqualTo(applicationSupport.resolve("data"));
        assertThat(directories.state()).isEqualTo(applicationSupport.resolve("state"));
        assertThat(directories.cache()).isEqualTo(path("/Users/test/Library/Caches/Quuz"));
        assertThat(directories.runtime()).isEmpty();
    }

    @Test
    void ignoresTheVendorAloneOnMacOS() {
        ApplicationDirectories directories = detect(OperatingSystem.MAC_OS).applicationDirectories("bar", "Quuz");

        assertThat(directories.config()).isEqualTo(path("/Users/test/Library/Application Support/Quuz/config"));
        assertThat(directories.cache()).isEqualTo(path("/Users/test/Library/Caches/Quuz"));
    }

    @Test
    void namesTheDirectoriesAfterTheBundleIdentifierOnMacOS() {
        ApplicationDirectories directories =
                detect(OperatingSystem.MAC_OS).applicationDirectories("foo", "bar baz", "Quuz Pro");

        assertThat(directories.config())
                .isEqualTo(path("/Users/test/Library/Application Support/foo.bar-baz.Quuz-Pro/config"));
        assertThat(directories.cache()).isEqualTo(path("/Users/test/Library/Caches/foo.bar-baz.Quuz-Pro"));
    }

    @Test
    void keepsEachKindApartInTheRoamingAndLocalApplicationDataOnWindows() {
        ApplicationDirectories directories = detect(OperatingSystem.WINDOWS).applicationDirectories("Quuz");

        Path roaming = path("C:\\Users\\test\\AppData\\Roaming").resolve("Quuz");
        Path local = path("C:\\Users\\test\\AppData\\Local").resolve("Quuz");
        assertThat(directories.config()).isEqualTo(roaming.resolve("config"));
        assertThat(directories.data()).isEqualTo(roaming.resolve("data"));
        assertThat(directories.state()).isEqualTo(local.resolve("state"));
        assertThat(directories.cache()).isEqualTo(local.resolve("cache"));
        assertThat(directories.runtime()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void nestsTheApplicationUnderItsVendorOnWindowsWhateverTheQualifier(boolean qualified) {
        Platform platform = detect(OperatingSystem.WINDOWS);
        ApplicationDirectories directories = qualified
                ? platform.applicationDirectories("foo", "bar", "Quuz")
                : platform.applicationDirectories("bar", "Quuz");

        assertThat(directories.config())
                .isEqualTo(path("C:\\Users\\test\\AppData\\Roaming")
                        .resolve("bar")
                        .resolve("Quuz")
                        .resolve("config"));
        assertThat(directories.cache())
                .isEqualTo(path("C:\\Users\\test\\AppData\\Local")
                        .resolve("bar")
                        .resolve("Quuz")
                        .resolve("cache"));
    }

    @ParameterizedTest
    @EnumSource(OperatingSystem.class)
    void neverSharesADirectoryBetweenKinds(OperatingSystem operatingSystem) {
        ApplicationDirectories directories = detect(operatingSystem).applicationDirectories("Quuz");

        assertThat(Set.copyOf(allPaths(directories))).hasSize(4);
    }

    @ParameterizedTest
    @EnumSource(OperatingSystem.class)
    void placesTheDirectoriesUnderTheFakeDirectories(OperatingSystem operatingSystem) {
        Platform platform = FakePlatform.newBuilder(operatingSystem, root).build();

        assertThat(allPaths(platform.applicationDirectories("foo", "bar", "Quuz")))
                .allSatisfy(directory -> assertThat(directory).startsWithRaw(root));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ".", "..", "a/b", "a\\b", "C:"})
    void rejectsWhatIsNotASingleName(String name) {
        Platform platform = detect(OperatingSystem.LINUX);

        assertThatIllegalArgumentException().isThrownBy(() -> platform.applicationDirectories(name));
        assertThatIllegalArgumentException().isThrownBy(() -> platform.applicationDirectories(name, "Quuz"));
        assertThatIllegalArgumentException().isThrownBy(() -> platform.applicationDirectories(name, "bar", "Quuz"));
    }

    // Each name is valid on the file systems of some platforms, but not on all.
    @ParameterizedTest
    @ValueSource(
            strings = {"a\u0000b", "a\tb", "a\nb", "a\u007fb", "a<b", "a>b", "a\"b", "a|b", "a?b", "a*b", " a", "a "})
    void rejectsWhatSomePlatformForbidsOnEveryPlatform(String name) {
        for (OperatingSystem operatingSystem : OperatingSystem.values()) {
            Platform platform = FakePlatform.newBuilder(operatingSystem, root).build();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> platform.applicationDirectories(name))
                    .withMessageContaining("application");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> platform.applicationDirectories(name, "Quuz"))
                    .withMessageContaining("vendor");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> platform.applicationDirectories(name, "bar", "Quuz"))
                    .withMessageContaining("qualifier");
        }
    }

    @Test
    void acceptsANameEveryPlatformAllows() {
        assertThatNoException().isThrownBy(() -> ApplicationDirectories.checkName("Quuz 2", "application"));
    }

    @Test
    void rejectsWhatIsNotANameNamingItsRole() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ApplicationDirectories.checkName("..", "vendor"))
                .withMessage("The vendor must be a name, not \"..\"");
    }

    @Test
    void rejectsLeadingOrTrailingWhitespaceNamingItsRole() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ApplicationDirectories.checkName(" bar", "vendor"))
                .withMessage("The vendor must not start or end with whitespace: \" bar\"");
    }

    @Test
    void rejectsForbiddenCharactersNamingItsRole() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ApplicationDirectories.checkName("a?b", "qualifier"))
                .withMessage("The qualifier must not contain a control character or any of /\\:<>\"|?*: \"a?b\"");
    }

    @Test
    void escapesControlCharactersInTheMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ApplicationDirectories.checkName("a\u0000b", "vendor"))
                .withMessage("The vendor must not contain a control character or any of /\\:<>\"|?*: \"a\\u0000b\"");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ApplicationDirectories.checkName("\t", "vendor"))
                .withMessage("The vendor must be a name, not \"\\u0009\"");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ApplicationDirectories.checkName("bar\n", "vendor"))
                .withMessage("The vendor must not start or end with whitespace: \"bar\\u000a\"");
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsANullNameOrRole() {
        assertThatNullPointerException()
                .isThrownBy(() -> ApplicationDirectories.checkName(null, "vendor"))
                .withMessage("vendor");
        assertThatNullPointerException()
                .isThrownBy(() -> ApplicationDirectories.checkName("bar", null))
                .withMessage("role");
    }
}
