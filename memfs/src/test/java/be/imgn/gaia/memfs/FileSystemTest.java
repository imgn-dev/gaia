package be.imgn.gaia.memfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class FileSystemTest {

    @Test
    void isInstalledForTheMemScheme() {
        assertThat(FileSystemProvider.installedProviders())
                .anySatisfy(provider -> assertThat(provider.getScheme()).isEqualTo("mem"));
    }

    @Test
    void hasAUniqueNameByDefault() throws IOException {
        try (FileSystem first = MemoryFileSystems.newFileSystem();
                FileSystem second = MemoryFileSystems.newFileSystem()) {
            assertThat(first).isNotSameAs(second);
            assertThat(first.toString()).startsWith("mem://").isNotEqualTo(second.toString());
            assertThat(first.isOpen()).isTrue();
            assertThat(first.isReadOnly()).isFalse();
            assertThat(first.provider().getScheme()).isEqualTo("mem");
            assertThat(first.supportedFileAttributeViews()).contains("posix");
            assertThat(first.getSeparator()).isEqualTo("/");
            assertThat(first.getRootDirectories()).extracting(Path::toString).containsExactly("/");
            assertThat(first.getFileStores()).hasSize(1);
        }
    }

    @Test
    void isFoundByItsUriWhileOpen() throws IOException {
        URI uri = URI.create("mem://found");
        FileSystem fileSystem = MemoryFileSystems.newFileSystem("found", Configuration.unix());
        Files.writeString(fileSystem.getPath("/file"), "content");

        assertThat(FileSystems.getFileSystem(uri)).isSameAs(fileSystem);
        assertThat(Files.readString(Path.of(URI.create("mem://found/file")))).isEqualTo("content");
        assertThat(fileSystem.getPath("/file").toUri()).isEqualTo(URI.create("mem://found/file"));

        fileSystem.close();

        assertThatExceptionOfType(FileSystemNotFoundException.class).isThrownBy(() -> FileSystems.getFileSystem(uri));
        assertThatExceptionOfType(FileSystemNotFoundException.class)
                .isThrownBy(() -> Path.of(URI.create("mem://found/file")));
        MemoryFileSystems.newFileSystem("found", Configuration.unix()).close();
    }

    @Test
    void isCreatedThroughTheJdkApi() throws IOException {
        try (FileSystem plain = FileSystems.newFileSystem(URI.create("mem://plain"), Map.of());
                FileSystem windows = FileSystems.newFileSystem(
                        URI.create("mem://windowed"), Map.of("configuration", Configuration.windows()))) {
            assertThat(plain.getSeparator()).isEqualTo("/");
            assertThat(windows.getSeparator()).isEqualTo("\\");
            assertThatIllegalArgumentException()
                    .isThrownBy(
                            () -> FileSystems.newFileSystem(URI.create("mem://bad"), Map.of("configuration", "unix")));
        }
    }

    @Test
    void refusesDuplicateAndInvalidNames() throws IOException {
        try (FileSystem taken = MemoryFileSystems.newFileSystem("taken", Configuration.unix())) {
            assertThatExceptionOfType(FileSystemAlreadyExistsException.class)
                    .isThrownBy(() -> MemoryFileSystems.newFileSystem("taken", Configuration.windows()))
                    .withMessage("taken");
            assertThat(taken.isOpen()).isTrue();
        }
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MemoryFileSystems.newFileSystem("with space", Configuration.unix()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MemoryFileSystems.newFileSystem("a/b", Configuration.unix()));
    }

    @Test
    void refusesUrisThatAreNotMemUris() throws IOException {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem("uris", Configuration.unix())) {
            FileSystemProvider provider = fileSystem.provider();

            assertThatIllegalArgumentException().isThrownBy(() -> provider.getFileSystem(URI.create("file:///")));
            assertThatIllegalArgumentException().isThrownBy(() -> provider.getFileSystem(URI.create("mem:/x")));
            assertThatIllegalArgumentException().isThrownBy(() -> provider.getPath(URI.create("mem://uris")));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> provider.newFileSystem(URI.create("mem:opaque"), Map.of()));
        }
    }

    @Test
    void refusesEverythingOnceClosed() throws IOException {
        FileSystem fileSystem = MemoryFileSystems.newFileSystem();
        Path file = Files.writeString(fileSystem.getPath("file"), "content");

        fileSystem.close();
        fileSystem.close();

        assertThat(fileSystem.isOpen()).isFalse();
        assertThatExceptionOfType(ClosedFileSystemException.class).isThrownBy(() -> Files.readString(file));
        assertThatExceptionOfType(ClosedFileSystemException.class).isThrownBy(() -> Files.exists(file));
        assertThatExceptionOfType(ClosedFileSystemException.class).isThrownBy(fileSystem::getRootDirectories);
        assertThatExceptionOfType(ClosedFileSystemException.class).isThrownBy(fileSystem::getFileStores);
        assertThatExceptionOfType(ClosedFileSystemException.class).isThrownBy(fileSystem::newWatchService);
        assertThatExceptionOfType(ClosedFileSystemException.class)
                .isThrownBy(fileSystem::getUserPrincipalLookupService);
        assertThatExceptionOfType(ClosedFileSystemException.class).isThrownBy(file::toAbsolutePath);
        assertThat(fileSystem.getPath("still", "parsed")).hasToString("still/parsed");
    }

    @Test
    void closesItsStreamsAndChannels() throws IOException {
        FileSystem fileSystem = MemoryFileSystems.newFileSystem();
        Path file = Files.writeString(fileSystem.getPath("file"), "content");
        var in = Files.newInputStream(file);
        var stream = fileSystem.provider().newDirectoryStream(fileSystem.getPath("/"), entry -> true);
        var entries = stream.iterator();

        fileSystem.close();

        assertThatExceptionOfType(IOException.class).isThrownBy(in::read);
        assertThat(entries.hasNext()).isFalse();
    }

    @Test
    void isSafeToUseFromManyThreads() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            Path directory = Files.createDirectory(fileSystem.getPath("/shared"));
            List<Future<?>> tasks = new ArrayList<>();
            for (int thread = 0; thread < 8; thread++) {
                int id = thread;
                tasks.add(executor.submit(() -> {
                    for (int i = 0; i < 50; i++) {
                        Path file = directory.resolve(id + "-" + i);
                        Files.writeString(file, "content " + i);
                        Files.readString(file);
                        Files.move(file, directory.resolve("moved-" + id + "-" + i));
                    }
                    return null;
                }));
            }
            for (Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }

            try (var entries = Files.list(directory)) {
                assertThat(entries.count()).isEqualTo(400);
            }
        } finally {
            executor.shutdown();
        }
    }
}
