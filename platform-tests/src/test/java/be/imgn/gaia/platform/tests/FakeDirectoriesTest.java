package be.imgn.gaia.platform.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.platform.Directories;
import be.imgn.gaia.platform.testing.FakeDirectories;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FakeDirectoriesTest {

    @TempDir
    Path root;

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

    private static FileSystem newZipFileSystem(Path directory) throws IOException {
        return FileSystems.newFileSystem(directory.resolve("directories.zip"), Map.of("create", "true"));
    }

    @Test
    void placesEachDirectoryInItsOwnSubdirectoryOfTheRoot() {
        FakeDirectories directories = FakeDirectories.newBuilder(root).build();

        assertThat(directories.home()).isEqualTo(root.resolve("home"));
        assertThat(directories.dataHome()).isEqualTo(root.resolve("data-home"));
        assertThat(directories.configHome()).isEqualTo(root.resolve("config-home"));
        assertThat(directories.stateHome()).isEqualTo(root.resolve("state-home"));
        assertThat(directories.cacheHome()).isEqualTo(root.resolve("cache-home"));
        assertThat(directories.runtimeDir()).contains(root.resolve("runtime-dir"));
        assertThat(directories.dataDirs()).containsExactly(root.resolve("data-dir"));
        assertThat(directories.configDirs()).containsExactly(root.resolve("config-dir"));
    }

    @Test
    void givesEveryDirectoryADistinctPathDirectlyUnderTheRoot() {
        List<Path> paths = allPaths(FakeDirectories.newBuilder(root).build());

        assertThat(paths).hasSize(8).doesNotHaveDuplicates().allSatisfy(path -> {
            assertThat(path.getParent()).isEqualTo(root);
            assertThat(path.isAbsolute()).isTrue();
        });
    }

    @Test
    void doesNotCreateTheDirectoriesByDefault() {
        FakeDirectories directories = FakeDirectories.newBuilder(root).build();

        assertThat(allPaths(directories)).allSatisfy(path -> assertThat(path).doesNotExist());
    }

    @Test
    void acceptsARootThatDoesNotExist() {
        Path missing = root.resolve("missing").resolve("root");

        FakeDirectories directories = FakeDirectories.newBuilder(missing).build();

        assertThat(directories.home()).isEqualTo(missing.resolve("home"));
        assertThat(missing).doesNotExist();
    }

    @Test
    void createsEveryDirectoryWhenAsked() {
        Path elsewhere = root.resolve("elsewhere").resolve("deep").resolve("cache");

        FakeDirectories directories = FakeDirectories.newBuilder(root.resolve("not-yet"))
                .cacheHome(elsewhere)
                .dataDirs(List.of(root.resolve("first"), root.resolve("second")))
                .createDirectories(true)
                .build();

        assertThat(allPaths(directories))
                .hasSize(9)
                .allSatisfy(path -> assertThat(path).isDirectory());
        assertThat(elsewhere).isDirectory();
    }

    @Test
    void createsTheDirectoriesThatAlreadyExistWithoutFailing() throws IOException {
        Files.createDirectories(root.resolve("home"));

        FakeDirectories directories =
                FakeDirectories.newBuilder(root).createDirectories(true).build();

        assertThat(directories.home()).isDirectory();
    }

    @Test
    void doesNotCreateTheRuntimeDirectoryOnceRemoved() {
        FakeDirectories.newBuilder(root)
                .withoutRuntimeDir()
                .createDirectories(true)
                .build();

        assertThat(root.resolve("runtime-dir")).doesNotExist();
        assertThat(root.resolve("home")).isDirectory();
    }

    @Test
    void createDirectoriesCanBeTurnedOffAgain() {
        FakeDirectories directories = FakeDirectories.newBuilder(root)
                .createDirectories(true)
                .createDirectories(false)
                .build();

        assertThat(directories.home()).doesNotExist();
    }

    @Test
    void failsWithAnUncheckedExceptionWhenADirectoryCannotBeCreated() throws IOException {
        Files.createFile(root.resolve("config-home"));

        FakeDirectories.Builder builder = FakeDirectories.newBuilder(root).createDirectories(true);

        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(builder::build)
                .withMessageContaining("config-home");
    }

    @ParameterizedTest
    @EnumSource(Directory.class)
    void replacesOneDirectoryWithoutChangingTheOthers(Directory directory) {
        Path replacement = root.resolve("replacement");
        List<Path> defaults = allPaths(FakeDirectories.newBuilder(root).build());

        List<Path> paths = allPaths(
                directory.set(FakeDirectories.newBuilder(root), replacement).build());

        assertThat(paths).hasSameSizeAs(defaults).containsOnlyOnce(replacement);
        for (int i = 0; i < paths.size(); i++) {
            if (!paths.get(i).equals(replacement)) {
                assertThat(paths.get(i)).isEqualTo(defaults.get(i));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Directory.class)
    void acceptsAReplacementOutsideTheRoot(Directory directory, @TempDir Path other) {
        FakeDirectories directories =
                directory.set(FakeDirectories.newBuilder(root), other).build();

        assertThat(directory.get(directories)).containsExactly(other);
    }

    @Test
    void acceptsTheSameDirectoryForSeveralOfThem() {
        Path applicationSupport = root.resolve("Library").resolve("Application Support");

        FakeDirectories directories = FakeDirectories.newBuilder(root)
                .dataHome(applicationSupport)
                .configHome(applicationSupport)
                .stateHome(applicationSupport)
                .build();

        assertThat(directories.dataHome())
                .isEqualTo(directories.configHome())
                .isEqualTo(directories.stateHome())
                .isEqualTo(applicationSupport);
    }

    @Test
    void removesTheRuntimeDirectory() {
        FakeDirectories directories =
                FakeDirectories.newBuilder(root).withoutRuntimeDir().build();

        assertThat(directories.runtimeDir()).isEmpty();
        assertThat(allPaths(directories)).hasSize(7).doesNotContain(root.resolve("runtime-dir"));
    }

    @Test
    void setsTheRuntimeDirectoryBackAfterRemovingIt() {
        FakeDirectories directories = FakeDirectories.newBuilder(root)
                .withoutRuntimeDir()
                .runtimeDir(root.resolve("run"))
                .build();

        assertThat(directories.runtimeDir()).contains(root.resolve("run"));
    }

    @Test
    void removesTheRuntimeDirectoryAfterSettingIt() {
        FakeDirectories directories = FakeDirectories.newBuilder(root)
                .runtimeDir(root.resolve("run"))
                .withoutRuntimeDir()
                .build();

        assertThat(directories.runtimeDir()).isEmpty();
    }

    @Test
    void keepsTheOrderOfTheSearchLists() {
        List<Path> data = List.of(root.resolve("c"), root.resolve("a"), root.resolve("b"));
        List<Path> config = List.of(root.resolve("z"), root.resolve("y"));

        FakeDirectories directories = FakeDirectories.newBuilder(root)
                .dataDirs(data)
                .configDirs(config)
                .build();

        assertThat(directories.dataDirs()).containsExactlyElementsOf(data);
        assertThat(directories.configDirs()).containsExactlyElementsOf(config);
    }

    @Test
    void acceptsEmptySearchLists() {
        FakeDirectories directories = FakeDirectories.newBuilder(root)
                .dataDirs(List.of())
                .configDirs(List.of())
                .build();

        assertThat(directories.dataDirs()).isEmpty();
        assertThat(directories.configDirs()).isEmpty();
    }

    @Test
    void copiesTheSearchLists() {
        List<Path> data = new ArrayList<>(List.of(root.resolve("a")));
        FakeDirectories.Builder builder = FakeDirectories.newBuilder(root).dataDirs(data);

        data.add(root.resolve("b"));

        assertThat(builder.build().dataDirs()).containsExactly(root.resolve("a"));
    }

    @Test
    void returnsUnmodifiableSearchLists() {
        FakeDirectories directories = FakeDirectories.newBuilder(root).build();

        assertThat(directories.dataDirs()).isUnmodifiable();
        assertThat(directories.configDirs()).isUnmodifiable();
    }

    @Test
    void buildsIndependentDirectoriesFromTheSameBuilder() {
        FakeDirectories.Builder builder = FakeDirectories.newBuilder(root);
        FakeDirectories first = builder.build();

        FakeDirectories second = builder.home(root.resolve("other")).build();

        assertThat(first.home()).isEqualTo(root.resolve("home"));
        assertThat(second.home()).isEqualTo(root.resolve("other"));
    }

    @Test
    void worksOnANonDefaultFileSystem() throws IOException {
        try (FileSystem zip = newZipFileSystem(root)) {
            Path zipRoot = zip.getPath("/fake");

            FakeDirectories directories =
                    FakeDirectories.newBuilder(zipRoot).createDirectories(true).build();

            assertThat(allPaths(directories)).hasSize(8).allSatisfy(path -> {
                assertThat(path.getFileSystem()).isSameAs(zip);
                assertThat(path.getParent()).isEqualTo(zipRoot);
                assertThat(Files.isDirectory(path)).isTrue();
            });
            assertThat(root.resolve("fake")).doesNotExist();
        }
    }

    @Test
    void rejectsARelativeRoot() {
        Path relative = Path.of("relative");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> FakeDirectories.newBuilder(relative))
                .withMessageContaining("absolute")
                .withMessageContaining("relative");
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsANullRoot() {
        assertThatNullPointerException()
                .isThrownBy(() -> FakeDirectories.newBuilder(null))
                .withMessageContaining("root");
    }

    @ParameterizedTest
    @EnumSource(Directory.class)
    void rejectsARelativeReplacement(Directory directory) {
        FakeDirectories.Builder builder = FakeDirectories.newBuilder(root);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> directory.set(builder, Path.of("relative")))
                .withMessageContaining(directory.parameter())
                .withMessageContaining("absolute");
    }

    @ParameterizedTest
    @EnumSource(Directory.class)
    void rejectsAReplacementOnAnotherFileSystem(Directory directory) throws IOException {
        FakeDirectories.Builder builder = FakeDirectories.newBuilder(root);
        try (FileSystem zip = newZipFileSystem(root)) {
            Path elsewhere = zip.getPath("/elsewhere");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> directory.set(builder, elsewhere))
                    .withMessageContaining(directory.parameter())
                    .withMessageContaining("file system");
        }
    }

    // Passing null is the point.
    @ParameterizedTest
    @EnumSource(Directory.class)
    @SuppressWarnings("NullAway")
    void rejectsANullReplacement(Directory directory) {
        FakeDirectories.Builder builder = FakeDirectories.newBuilder(root);

        assertThatNullPointerException().isThrownBy(() -> directory.set(builder, null));
    }

    @Test
    void keepsTheEarlierDirectoriesWhenAReplacementIsRejected() {
        FakeDirectories.Builder builder = FakeDirectories.newBuilder(root);
        List<Path> invalid = Arrays.asList(root.resolve("valid"), Path.of("relative"));

        assertThatIllegalArgumentException().isThrownBy(() -> builder.dataDirs(invalid));
        assertThat(builder.build().dataDirs()).containsExactly(root.resolve("data-dir"));
    }

    @Test
    void namesTheIndexOfARejectedDirectoryInAList() {
        FakeDirectories.Builder builder = FakeDirectories.newBuilder(root);
        List<Path> invalid = List.of(root.resolve("valid"), Path.of("relative"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.dataDirs(invalid))
                .withMessageStartingWith("dataDirs[1] ");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.configDirs(invalid))
                .withMessageStartingWith("configDirs[1] ");
    }

    /** Each directory of a {@link FakeDirectories}, to set and get it in parameterized tests. */
    enum Directory {
        HOME("home"),
        DATA_HOME("dataHome"),
        CONFIG_HOME("configHome"),
        STATE_HOME("stateHome"),
        CACHE_HOME("cacheHome"),
        RUNTIME_DIR("runtimeDir"),
        DATA_DIRS("dataDirs"),
        CONFIG_DIRS("configDirs");

        private final String parameter;

        Directory(String parameter) {
            this.parameter = parameter;
        }

        String parameter() {
            return parameter;
        }

        FakeDirectories.Builder set(FakeDirectories.Builder builder, Path path) {
            return switch (this) {
                case HOME -> builder.home(path);
                case DATA_HOME -> builder.dataHome(path);
                case CONFIG_HOME -> builder.configHome(path);
                case STATE_HOME -> builder.stateHome(path);
                case CACHE_HOME -> builder.cacheHome(path);
                case RUNTIME_DIR -> builder.runtimeDir(path);
                // Arrays.asList, unlike List.of, lets a null path through to the builder.
                case DATA_DIRS -> builder.dataDirs(Arrays.asList(path));
                case CONFIG_DIRS -> builder.configDirs(Arrays.asList(path));
            };
        }

        List<Path> get(Directories directories) {
            return switch (this) {
                case HOME -> List.of(directories.home());
                case DATA_HOME -> List.of(directories.dataHome());
                case CONFIG_HOME -> List.of(directories.configHome());
                case STATE_HOME -> List.of(directories.stateHome());
                case CACHE_HOME -> List.of(directories.cacheHome());
                case RUNTIME_DIR -> directories.runtimeDir().stream().toList();
                case DATA_DIRS -> directories.dataDirs();
                case CONFIG_DIRS -> directories.configDirs();
            };
        }
    }
}
