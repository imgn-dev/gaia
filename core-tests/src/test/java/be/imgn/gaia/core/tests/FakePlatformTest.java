package be.imgn.gaia.core.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.platform.Directories;
import be.imgn.gaia.core.platform.Platform;
import be.imgn.gaia.core.testing.FakeDirectories;
import be.imgn.gaia.core.testing.FakePlatform;
import be.imgn.gaia.core.testing.OperatingSystem;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class FakePlatformTest {

    @TempDir
    Path root;

    private static FileSystem newZipFileSystem(Path directory) throws IOException {
        return FileSystems.newFileSystem(directory.resolve("platform.zip"), Map.of("create", "true"));
    }

    @ParameterizedTest
    @CsvSource({
        "LINUX,      true,  false, false",
        "MAC_OS,     false, true,  false",
        "WINDOWS,    false, false, true",
        "OTHER_UNIX, false, false, false",
    })
    void runsTheGivenOperatingSystem(OperatingSystem operatingSystem, boolean linux, boolean macOS, boolean windows) {
        Platform platform = FakePlatform.newBuilder(operatingSystem, root).build();

        assertThat(platform.isLinux()).isEqualTo(linux);
        assertThat(platform.isMacOS()).isEqualTo(macOS);
        assertThat(platform.isWindows()).isEqualTo(windows);
    }

    @ParameterizedTest
    @EnumSource(OperatingSystem.class)
    void runsAtMostOneOfTheOperatingSystemsItCanTellApart(OperatingSystem operatingSystem) {
        Platform platform = FakePlatform.newBuilder(operatingSystem, root).build();

        assertThat(Stream.of(platform.isLinux(), platform.isMacOS(), platform.isWindows())
                        .filter(Boolean::booleanValue)
                        .count())
                .isEqualTo(operatingSystem == OperatingSystem.OTHER_UNIX ? 0 : 1);
    }

    @ParameterizedTest
    @EnumSource(OperatingSystem.class)
    void placesItsDirectoriesUnderTheRootByDefault(OperatingSystem operatingSystem) {
        FakeDirectories expected = FakeDirectories.newBuilder(root).build();

        Platform platform = FakePlatform.newBuilder(operatingSystem, root).build();

        assertThat(platform.directories().home()).isEqualTo(expected.home());
        assertThat(platform.directories().dataHome()).isEqualTo(expected.dataHome());
        assertThat(platform.directories().configHome()).isEqualTo(expected.configHome());
        assertThat(platform.directories().stateHome()).isEqualTo(expected.stateHome());
        assertThat(platform.directories().cacheHome()).isEqualTo(expected.cacheHome());
        assertThat(platform.directories().runtimeDir()).isEqualTo(expected.runtimeDir());
        assertThat(platform.directories().dataDirs()).isEqualTo(expected.dataDirs());
        assertThat(platform.directories().configDirs()).isEqualTo(expected.configDirs());
    }

    @Test
    void usesTheFileSystemOfTheRoot() {
        Platform platform = FakePlatform.newBuilder(OperatingSystem.LINUX, root).build();

        assertThat(platform.fileSystem()).isSameAs(root.getFileSystem());
    }

    @ParameterizedTest
    @EnumSource(OperatingSystem.class)
    void worksOnANonDefaultFileSystem(OperatingSystem operatingSystem) throws IOException {
        try (FileSystem zip = newZipFileSystem(root)) {
            Platform platform =
                    FakePlatform.newBuilder(operatingSystem, zip.getPath("/")).build();

            assertThat(platform.fileSystem()).isSameAs(zip);
            assertThat(platform.directories().home()).isEqualTo(zip.getPath("/home"));
            assertThat(platform.directories().configDirs())
                    .allSatisfy(path -> assertThat(path.getFileSystem()).isSameAs(zip));
        }
    }

    @Test
    void usesTheGivenDirectories() {
        FakeDirectories directories = FakeDirectories.newBuilder(root.resolve("elsewhere"))
                .withoutRuntimeDir()
                .build();

        Platform platform = FakePlatform.newBuilder(OperatingSystem.MAC_OS, root)
                .directories(directories)
                .build();

        assertThat(platform.directories()).isSameAs(directories);
        assertThat(platform.directories().runtimeDir()).isEmpty();
    }

    @Test
    void doesNotCreateTheDefaultDirectories() {
        Platform platform = FakePlatform.newBuilder(OperatingSystem.LINUX, root).build();

        assertThat(platform.directories().home()).doesNotExist();
    }

    @Test
    void rejectsDirectoriesOnAnotherFileSystem() throws IOException {
        FakePlatform.Builder builder = FakePlatform.newBuilder(OperatingSystem.LINUX, root);
        try (FileSystem zip = newZipFileSystem(root)) {
            FakeDirectories directories =
                    FakeDirectories.newBuilder(zip.getPath("/")).build();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> builder.directories(directories))
                    .withMessageContaining("file system");
        }
    }

    @Test
    void rejectsDirectoriesWithASingleDirectoryOnAnotherFileSystem() throws IOException {
        FakePlatform.Builder builder = FakePlatform.newBuilder(OperatingSystem.LINUX, root);
        FakeDirectories local = FakeDirectories.newBuilder(root).build();
        try (FileSystem zip = newZipFileSystem(root)) {
            Directories mixed = new Directories() {
                @Override
                public Path home() {
                    return local.home();
                }

                @Override
                public Path dataHome() {
                    return local.dataHome();
                }

                @Override
                public Path configHome() {
                    return local.configHome();
                }

                @Override
                public Path stateHome() {
                    return local.stateHome();
                }

                @Override
                public Path cacheHome() {
                    return local.cacheHome();
                }

                @Override
                public Optional<Path> runtimeDir() {
                    return local.runtimeDir();
                }

                @Override
                public List<Path> dataDirs() {
                    return local.dataDirs();
                }

                @Override
                public List<Path> configDirs() {
                    return List.of(local.configDirs().get(0), zip.getPath("/etc/xdg"));
                }
            };

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> builder.directories(mixed))
                    .withMessageContaining("/etc/xdg");
        }
    }

    @Test
    void keepsTheEarlierDirectoriesWhenNewOnesAreRejected() throws IOException {
        FakePlatform.Builder builder = FakePlatform.newBuilder(OperatingSystem.LINUX, root);
        try (FileSystem zip = newZipFileSystem(root)) {
            FakeDirectories directories =
                    FakeDirectories.newBuilder(zip.getPath("/")).build();

            assertThatIllegalArgumentException().isThrownBy(() -> builder.directories(directories));
        }
        assertThat(builder.build().directories().home()).isEqualTo(root.resolve("home"));
    }

    @Test
    void rejectsARelativeRoot() {
        Path relative = Path.of("relative");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> FakePlatform.newBuilder(OperatingSystem.LINUX, relative))
                .withMessageContaining("absolute");
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullArguments() {
        FakePlatform.Builder builder = FakePlatform.newBuilder(OperatingSystem.LINUX, root);

        assertThatNullPointerException()
                .isThrownBy(() -> FakePlatform.newBuilder(null, root))
                .withMessageContaining("operatingSystem");
        assertThatNullPointerException()
                .isThrownBy(() -> FakePlatform.newBuilder(OperatingSystem.LINUX, null))
                .withMessageContaining("root");
        assertThatNullPointerException()
                .isThrownBy(() -> builder.directories(null))
                .withMessageContaining("directories");
    }
}
