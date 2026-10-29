package be.imgn.gaia.memfs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotLinkException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FilesTest {

    private final FileSystem fileSystem = MemoryFileSystems.newFileSystem();

    @AfterEach
    void close() throws IOException {
        fileSystem.close();
    }

    private Path path(String path) {
        return fileSystem.getPath(path);
    }

    @Nested
    class Creating {

        @Test
        void createsTheWorkingDirectoryWithTheFileSystem() {
            assertThat(Files.isDirectory(path("/work"))).isTrue();
            assertThat(Files.isDirectory(path(""))).isTrue();
            assertThat(Files.isDirectory(path("/"))).isTrue();
        }

        @Test
        void createsFilesAndDirectories() throws IOException {
            Files.createDirectory(path("dir"));
            Files.createFile(path("dir/file"));
            Files.createDirectories(path("/a/b/c"));

            assertThat(Files.isDirectory(path("/work/dir"))).isTrue();
            assertThat(Files.isRegularFile(path("dir/file"))).isTrue();
            assertThat(Files.size(path("dir/file"))).isZero();
            assertThat(Files.isDirectory(path("/a/b/c"))).isTrue();
            assertThat(Files.exists(path("missing"))).isFalse();
            assertThat(Files.notExists(path("missing"))).isTrue();
        }

        @Test
        void refusesToCreateWhatExists() throws IOException {
            Files.createDirectory(path("dir"));
            Files.createFile(path("file"));

            assertThatExceptionOfType(FileAlreadyExistsException.class)
                    .isThrownBy(() -> Files.createDirectory(path("dir")))
                    .withMessage("dir");
            assertThatExceptionOfType(FileAlreadyExistsException.class)
                    .isThrownBy(() -> Files.createFile(path("file")));
            assertThatExceptionOfType(FileAlreadyExistsException.class).isThrownBy(() -> Files.createFile(path("dir")));
            assertThatExceptionOfType(FileAlreadyExistsException.class)
                    .isThrownBy(() -> Files.createDirectory(path("/")));
            assertThatExceptionOfType(FileSystemException.class)
                    .isThrownBy(() -> Files.createDirectories(path("file/sub")));
        }

        @Test
        void needsTheDirectoryToBeThere() {
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> Files.createFile(path("missing/file")))
                    .withMessage("missing/file");
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> Files.createDirectory(path("missing/dir")));
        }

        @Test
        void cannotGoThroughAFile() throws IOException {
            Files.createFile(path("file"));

            assertThatExceptionOfType(FileSystemException.class)
                    .isThrownBy(() -> Files.createFile(path("file/child")))
                    .withMessage("file/child: Not a directory");
        }

        @Test
        void limitsTheLengthOfNames() throws IOException {
            try (FileSystem small = MemoryFileSystems.newFileSystem(
                    Configuration.unix().toBuilder().maxNameLength(4).build())) {
                Files.createFile(small.getPath("abcd"));

                assertThatExceptionOfType(FileSystemException.class)
                        .isThrownBy(() -> Files.createFile(small.getPath("abcde")))
                        .withMessage("abcde: File name too long");
            }
        }

        @Test
        void refusesWindowsReservedNames() throws IOException {
            try (FileSystem windows = MemoryFileSystems.newFileSystem(Configuration.windows())) {
                for (String name : List.of("CON", "nul", "Com1", "LPT9.txt", "aux.tar.gz")) {
                    assertThatExceptionOfType(FileSystemException.class)
                            .as(name)
                            .isThrownBy(() -> Files.createFile(windows.getPath(name)))
                            .withMessageEndingWith("Reserved name");
                }
                Files.createFile(windows.getPath("CONSOLE"));
                Files.createFile(windows.getPath("COM0"));
            }
            Files.createFile(path("CON"));
        }

        @Test
        void givesAttributesToNewFiles() throws IOException {
            Path file = Files.createFile(
                    path("secret"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Path directory = Files.createDirectory(
                    path("private"),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));

            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
                    .isEqualTo("rw-------");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(directory)))
                    .isEqualTo("rwx------");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(path("/work"))))
                    .isEqualTo("rwxr-xr-x");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(Files.createFile(path("plain")))))
                    .isEqualTo("rw-r--r--");
        }

        @Test
        void refusesAttributesOfUnsupportedViews() throws IOException {
            try (FileSystem windows = MemoryFileSystems.newFileSystem(Configuration.windows())) {
                assertThatExceptionOfType(UnsupportedOperationException.class)
                        .isThrownBy(() -> Files.createFile(
                                windows.getPath("file"),
                                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))));
            }
        }

        @Test
        void createsTemporaryFiles() throws IOException {
            Path directory = Files.createTempDirectory(path("/"), "temp");
            Path file = Files.createTempFile(directory, "prefix", ".suffix");

            assertThat(Files.isRegularFile(file)).isTrue();
            assertThat(file.getFileName().toString()).startsWith("prefix").endsWith(".suffix");
        }
    }

    @Nested
    class ReadingAndWriting {

        @Test
        void writesAndReadsContent() throws IOException {
            Path file = path("file.txt");

            Files.writeString(file, "Hello");
            Files.writeString(file, ", world", StandardOpenOption.APPEND);

            assertThat(Files.readString(file)).isEqualTo("Hello, world");
            assertThat(Files.readAllLines(path("/work/file.txt"))).containsExactly("Hello, world");
            assertThat(Files.size(file)).isEqualTo(12);
            Files.write(file, List.of("a", "b"));
            assertThat(Files.readAllLines(file)).containsExactly("a", "b");
        }

        @Test
        void writesLargeFilesAcrossBlocks() throws IOException {
            try (FileSystem small = MemoryFileSystems.newFileSystem(
                    Configuration.unix().toBuilder().blockSize(16).build())) {
                byte[] content = new byte[1000];
                for (int i = 0; i < content.length; i++) {
                    content[i] = (byte) i;
                }
                Path file = small.getPath("big");

                Files.write(file, content);

                assertThat(Files.readAllBytes(file)).isEqualTo(content);
                try (var in = Files.newInputStream(file)) {
                    assertThat(in.skip(990)).isEqualTo(990);
                    assertThat(in.readAllBytes()).hasSize(10);
                }
            }
        }

        @Test
        void truncatesWhenAskedTo() throws IOException {
            Path file = path("file");
            Files.writeString(file, "long content");

            Files.writeString(file, "short", StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(file, "x", StandardOpenOption.WRITE);

            assertThat(Files.readString(file)).isEqualTo("xhort");
        }

        @Test
        void reportsMissingFiles() {
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> Files.readString(path("missing")))
                    .withMessage("missing");
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> Files.writeString(path("missing"), "x", StandardOpenOption.WRITE));
            assertThatExceptionOfType(NoSuchFileException.class).isThrownBy(() -> Files.size(path("missing")));
        }

        @Test
        void refusesToOpenDirectories() {
            assertThatExceptionOfType(FileSystemException.class)
                    .isThrownBy(() -> Files.readString(path("/work")))
                    .withMessage("/work: Is a directory");
            assertThatExceptionOfType(FileSystemException.class).isThrownBy(() -> Files.writeString(path("/"), "x"));
        }

        @Test
        void refusesToCreateWhatExistsWithCreateNew() throws IOException {
            Files.createFile(path("file"));

            assertThatExceptionOfType(FileAlreadyExistsException.class)
                    .isThrownBy(() -> Files.writeString(path("file"), "x", StandardOpenOption.CREATE_NEW));
            Files.newByteChannel(path("file"), StandardOpenOption.READ, StandardOpenOption.CREATE_NEW)
                    .close();
        }

        @Test
        void refusesConflictingOptions() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() ->
                            Files.newByteChannel(path("file"), StandardOpenOption.READ, StandardOpenOption.APPEND));
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> Files.newByteChannel(
                            path("file"),
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND,
                            StandardOpenOption.TRUNCATE_EXISTING));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> Files.newByteChannel(path("file"), new OpenOption() {}));
        }

        @Test
        void runsOutOfSpace() throws IOException {
            try (FileSystem tiny = MemoryFileSystems.newFileSystem(
                    Configuration.unix().toBuilder().blockSize(10).maxSize(100).build())) {
                Path file = tiny.getPath("file");
                Files.write(file, new byte[100]);

                assertThat(tiny.getFileStores().iterator().next().getUnallocatedSpace())
                        .isZero();
                assertThatExceptionOfType(FileSystemException.class)
                        .isThrownBy(() -> Files.write(tiny.getPath("more"), new byte[1]))
                        .withMessage("more: No space left on device");

                Files.delete(file);

                assertThat(tiny.getFileStores().iterator().next().getUnallocatedSpace())
                        .isEqualTo(100);
                Files.write(tiny.getPath("more"), new byte[1]);
            }
        }

        @Test
        void readsZerosPastTruncatedContent() throws IOException {
            Path file = path("file");
            Files.write(file, new byte[] {1, 2, 3, 4});
            try (var channel = Files.newByteChannel(file, StandardOpenOption.WRITE)) {
                channel.truncate(1);
                channel.position(3);
                channel.write(ByteBuffer.wrap(new byte[] {9}));
            }

            assertThat(Files.readAllBytes(file)).containsExactly(1, 0, 0, 9);
        }
    }

    @Nested
    class Deleting {

        @Test
        void deletesFilesAndEmptyDirectories() throws IOException {
            Files.createDirectory(path("dir"));
            Files.createFile(path("file"));

            Files.delete(path("file"));
            Files.delete(path("dir"));

            assertThat(Files.exists(path("file"))).isFalse();
            assertThat(Files.exists(path("dir"))).isFalse();
            assertThat(Files.deleteIfExists(path("file"))).isFalse();
        }

        @Test
        void refusesToDeleteWhatCannotBe() throws IOException {
            Files.createDirectories(path("dir/sub"));

            assertThatExceptionOfType(DirectoryNotEmptyException.class)
                    .isThrownBy(() -> Files.delete(path("dir")))
                    .withMessage("dir");
            assertThatExceptionOfType(NoSuchFileException.class).isThrownBy(() -> Files.delete(path("missing")));
            assertThatExceptionOfType(FileSystemException.class)
                    .isThrownBy(() -> Files.delete(path("/")))
                    .withMessageContaining("Cannot delete a root directory");
        }

        @Test
        void keepsTheContentOfAFileOpenAfterItIsDeleted() throws IOException {
            Path file = path("file");
            Files.writeString(file, "still here");

            try (var channel = Files.newByteChannel(file)) {
                Files.delete(file);
                var buffer = ByteBuffer.allocate(20);
                channel.read(buffer);

                assertThat(new String(buffer.array(), 0, buffer.position(), UTF_8))
                        .isEqualTo("still here");
                assertThat(Files.exists(file)).isFalse();
            }
        }

        @Test
        void deletesOnClose() throws IOException {
            Path file = path("temporary");

            try (var channel = Files.newByteChannel(
                    file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.DELETE_ON_CLOSE)) {
                channel.write(ByteBuffer.wrap(new byte[] {1}));
                assertThat(Files.exists(file)).isTrue();
            }

            assertThat(Files.exists(file)).isFalse();
        }
    }

    @Nested
    class Copying {

        @Test
        void copiesFilesWithTheirContent() throws IOException {
            Files.writeString(path("source"), "content");

            Files.copy(path("source"), path("target"));
            Files.writeString(path("source"), "changed");

            assertThat(Files.readString(path("target"))).isEqualTo("content");
        }

        @Test
        void copiesDirectoriesWithoutTheirEntries() throws IOException {
            Files.createDirectories(path("dir/sub"));

            Files.copy(path("dir"), path("copy"));

            assertThat(Files.isDirectory(path("copy"))).isTrue();
            assertThat(Files.exists(path("copy/sub"))).isFalse();
        }

        @Test
        void replacesOnlyWhenAskedTo() throws IOException {
            Files.writeString(path("source"), "new");
            Files.writeString(path("target"), "old");
            Files.createDirectories(path("full/sub"));

            assertThatExceptionOfType(FileAlreadyExistsException.class)
                    .isThrownBy(() -> Files.copy(path("source"), path("target")))
                    .withMessage("target");
            assertThatExceptionOfType(DirectoryNotEmptyException.class)
                    .isThrownBy(() -> Files.copy(path("source"), path("full"), StandardCopyOption.REPLACE_EXISTING));
            Files.copy(path("source"), path("target"), StandardCopyOption.REPLACE_EXISTING);

            assertThat(Files.readString(path("target"))).isEqualTo("new");
        }

        @Test
        void copiesAttributesWhenAskedTo() throws IOException {
            Path source = Files.writeString(path("source"), "x");
            Files.setLastModifiedTime(source, FileTime.fromMillis(1000));
            Files.setPosixFilePermissions(source, PosixFilePermissions.fromString("r--------"));

            Files.copy(source, path("plain"));
            Files.copy(source, path("same"), StandardCopyOption.COPY_ATTRIBUTES);

            assertThat(Files.getLastModifiedTime(path("same")).toMillis()).isEqualTo(1000);
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(path("same"))))
                    .isEqualTo("r--------");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(path("plain"))))
                    .isEqualTo("rw-r--r--");
        }

        @Test
        void copiesSymbolicLinksOrWhatTheyPointTo() throws IOException {
            Files.writeString(path("target"), "content");
            Files.createSymbolicLink(path("link"), path("target"));

            Files.copy(path("link"), path("followed"));
            Files.copy(path("link"), path("copied"), LinkOption.NOFOLLOW_LINKS);

            assertThat(Files.isSymbolicLink(path("followed"))).isFalse();
            assertThat(Files.readString(path("followed"))).isEqualTo("content");
            assertThat(Files.readSymbolicLink(path("copied"))).isEqualTo(path("target"));
        }

        @Test
        void copiesAFileOntoItselfAsANoOp() throws IOException {
            Files.writeString(path("file"), "content");

            Files.copy(path("file"), path("/work/file"));

            assertThat(Files.readString(path("file"))).isEqualTo("content");
        }

        @Test
        void copiesAcrossFileSystems() throws IOException {
            Files.writeString(path("source"), "content");
            try (FileSystem other = MemoryFileSystems.newFileSystem(Configuration.windows())) {
                Path target = other.getPath("C:\\target.txt");

                Files.copy(path("source"), target);

                assertThat(Files.readString(target)).isEqualTo("content");
                Files.copy(target, path("back"));
                assertThat(Files.readString(path("back"))).isEqualTo("content");
            }
        }

        @Test
        void refusesAtomicCopiesAndMissingSources() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> Files.copy(path("a"), path("b"), StandardCopyOption.ATOMIC_MOVE));
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> Files.copy(path("missing"), path("b")));
        }

        @Test
        void copiesToAndFromStreams() throws IOException {
            Files.copy(new ByteArrayInputStream(new byte[] {1, 2}), path("file"));
            var out = new ByteArrayOutputStream();

            Files.copy(path("file"), out);

            assertThat(out.toByteArray()).containsExactly(1, 2);
        }
    }

    @Nested
    class Moving {

        @Test
        void movesFilesAndDirectories() throws IOException {
            Files.createDirectories(path("dir/sub"));
            Files.writeString(path("dir/sub/file"), "content");

            Files.move(path("dir"), path("/moved"));

            assertThat(Files.exists(path("dir"))).isFalse();
            assertThat(Files.readString(path("/moved/sub/file"))).isEqualTo("content");
            assertThat(path("/moved/sub/..").toRealPath()).hasToString("/moved");
        }

        @Test
        void replacesOnlyWhenAskedOrAtomic() throws IOException {
            Files.writeString(path("a"), "a");
            Files.writeString(path("b"), "b");
            Files.writeString(path("c"), "c");

            assertThatExceptionOfType(FileAlreadyExistsException.class)
                    .isThrownBy(() -> Files.move(path("a"), path("b")));
            Files.move(path("a"), path("b"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(path("b"), path("c"), StandardCopyOption.ATOMIC_MOVE);

            assertThat(Files.readString(path("c"))).isEqualTo("a");
            assertThat(Files.exists(path("a"))).isFalse();
            assertThat(Files.exists(path("b"))).isFalse();
        }

        @Test
        void refusesToMoveADirectoryIntoItself() throws IOException {
            Files.createDirectories(path("dir/sub"));

            assertThatExceptionOfType(FileSystemException.class)
                    .isThrownBy(() -> Files.move(path("dir"), path("dir/sub/inside")))
                    .withMessageEndingWith("Invalid argument");
            assertThatExceptionOfType(FileSystemException.class)
                    .isThrownBy(() -> Files.move(path("/"), path("dir/root")));
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> Files.move(path("missing"), path("x")));
        }

        @Test
        void refusesToReplaceARoot() throws IOException {
            try (FileSystem windows = MemoryFileSystems.newFileSystem(
                    Configuration.windows().toBuilder().roots("C:\\", "D:\\").build())) {
                Path file = windows.getPath("file.txt");
                Files.writeString(file, "x");
                Path root = windows.getPath("D:\\");

                assertThatExceptionOfType(FileSystemException.class)
                        .isThrownBy(() -> Files.copy(file, root, StandardCopyOption.REPLACE_EXISTING))
                        .withMessageContaining("Cannot replace a root directory");
                assertThatExceptionOfType(FileSystemException.class)
                        .isThrownBy(() -> Files.move(file, root, StandardCopyOption.REPLACE_EXISTING))
                        .withMessageContaining("Cannot replace a root directory");
                try (var entries = Files.list(root)) {
                    assertThat(entries).isEmpty();
                }
            }
        }

        @Test
        void renamesWithinTheSameEntryOnCaseInsensitiveFileSystems() throws IOException {
            try (FileSystem windows = MemoryFileSystems.newFileSystem(Configuration.windows())) {
                Files.writeString(windows.getPath("readme.txt"), "x");

                Files.move(windows.getPath("readme.txt"), windows.getPath("README.TXT"));

                try (var entries = Files.list(windows.getPath("C:\\work"))) {
                    assertThat(entries.map(entry -> entry.getFileName().toString()))
                            .containsExactly("README.TXT");
                }
            }
        }

        @Test
        void movesAcrossFileSystemsByCopying() throws IOException {
            Files.writeString(path("file"), "content");
            Files.createDirectories(path("full/sub"));
            try (FileSystem other = MemoryFileSystems.newFileSystem()) {
                Files.move(path("file"), other.getPath("/moved"));

                assertThat(Files.readString(other.getPath("/moved"))).isEqualTo("content");
                assertThat(Files.exists(path("file"))).isFalse();
                assertThatExceptionOfType(AtomicMoveNotSupportedException.class)
                        .isThrownBy(() ->
                                Files.move(other.getPath("/moved"), path("back"), StandardCopyOption.ATOMIC_MOVE));
                assertThatExceptionOfType(DirectoryNotEmptyException.class)
                        .isThrownBy(() -> Files.move(path("full"), other.getPath("/full")));
            }
        }

        @Test
        void refusesPathsOfOtherProviders() {
            assertThatExceptionOfType(ProviderMismatchException.class)
                    .isThrownBy(() -> fileSystem.provider().move(path("a"), Path.of("b")));
        }
    }

    @Nested
    class Links {

        @Test
        void followsSymbolicLinks() throws IOException {
            Files.createDirectories(path("/data/dir"));
            Files.writeString(path("/data/dir/file"), "content");
            Files.createSymbolicLink(path("absolute"), path("/data/dir"));
            Files.createSymbolicLink(path("/data/relative"), path("dir/file"));

            assertThat(Files.readString(path("absolute/file"))).isEqualTo("content");
            assertThat(Files.readString(path("/data/relative"))).isEqualTo("content");
            assertThat(Files.isSymbolicLink(path("absolute"))).isTrue();
            assertThat(Files.isDirectory(path("absolute"))).isTrue();
            assertThat(Files.isDirectory(path("absolute"), LinkOption.NOFOLLOW_LINKS))
                    .isFalse();
            assertThat(Files.readSymbolicLink(path("/data/relative"))).hasToString("dir/file");
            assertThat(Files.size(path("/data/relative"))).isEqualTo(7);
            assertThat(path("absolute/../dir").toRealPath()).hasToString("/data/dir");
        }

        @Test
        void detectsLoops() throws IOException {
            Files.createSymbolicLink(path("a"), path("b"));
            Files.createSymbolicLink(path("b"), path("a"));

            assertThatExceptionOfType(FileSystemException.class)
                    .isThrownBy(() -> Files.readString(path("a")))
                    .withMessage("a: Too many levels of symbolic links");
            assertThat(Files.exists(path("a"))).isFalse();
            assertThat(Files.exists(path("a"), LinkOption.NOFOLLOW_LINKS)).isTrue();
        }

        @Test
        void danglesWhenTheTargetIsGone() throws IOException {
            Files.createSymbolicLink(path("dangling"), path("missing"));

            assertThat(Files.exists(path("dangling"))).isFalse();
            assertThatExceptionOfType(NoSuchFileException.class).isThrownBy(() -> Files.readString(path("dangling")));
            Files.writeString(path("dangling"), "created through the link");
            assertThat(Files.readString(path("missing"))).isEqualTo("created through the link");
        }

        @Test
        void refusesToCreateANewFileThroughALink() throws IOException {
            Files.createSymbolicLink(path("dangling"), path("missing"));

            assertThatExceptionOfType(FileAlreadyExistsException.class)
                    .isThrownBy(() -> Files.newByteChannel(
                                    path("dangling"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                            .close());
            assertThat(Files.exists(path("missing"), LinkOption.NOFOLLOW_LINKS)).isFalse();
        }

        @Test
        void refusesWhatIsNotALink() throws IOException {
            Files.createFile(path("file"));
            Files.createSymbolicLink(path("link"), path("file"));

            assertThatExceptionOfType(NotLinkException.class)
                    .isThrownBy(() -> Files.readSymbolicLink(path("file")))
                    .withMessage("file");
            assertThatExceptionOfType(FileAlreadyExistsException.class)
                    .isThrownBy(() -> Files.createSymbolicLink(path("link"), path("other")));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> Files.createSymbolicLink(
                            path("withAttributes"),
                            path("file"),
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx"))));
            assertThatExceptionOfType(FileSystemException.class)
                    .isThrownBy(() -> Files.newByteChannel(path("link"), LinkOption.NOFOLLOW_LINKS))
                    .withMessageContaining("Too many levels of symbolic links");
        }

        @Test
        void sharesContentThroughHardLinks() throws IOException {
            Files.writeString(path("original"), "shared");
            Files.createLink(path("alias"), path("original"));

            Files.writeString(path("alias"), "changed");
            Files.delete(path("original"));

            assertThat(Files.readString(path("alias"))).isEqualTo("changed");
            assertThat(Files.getAttribute(path("alias"), "unix:nlink")).isEqualTo(1);
            assertThat(Files.isSameFile(path("alias"), path("/work/alias"))).isTrue();
        }

        @Test
        void staysOnItsFileSystem() throws IOException {
            Files.createFile(path("file"));
            try (FileSystem other = MemoryFileSystems.newFileSystem()) {
                Path foreign = Files.createFile(other.getPath("/foreign"));

                assertThatExceptionOfType(ProviderMismatchException.class)
                        .isThrownBy(() -> Files.createSymbolicLink(path("link"), foreign));
                assertThatExceptionOfType(ProviderMismatchException.class)
                        .isThrownBy(() -> Files.createLink(path("link"), foreign));
                assertThatExceptionOfType(ProviderMismatchException.class)
                        .isThrownBy(() -> Files.createLink(path("link"), Path.of("x")));
            }
        }

        @Test
        void refusesHardLinksToDirectoriesOrOverFiles() throws IOException {
            Files.createDirectory(path("dir"));
            Files.createFile(path("file"));

            assertThatExceptionOfType(FileSystemException.class)
                    .isThrownBy(() -> Files.createLink(path("alias"), path("dir")))
                    .withMessageEndingWith("Operation not permitted");
            assertThatExceptionOfType(FileAlreadyExistsException.class)
                    .isThrownBy(() -> Files.createLink(path("file"), path("file")));
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> Files.createLink(path("alias"), path("missing")));
        }
    }

    @Nested
    class Queries {

        @Test
        void tellsWhetherFilesAreTheSame() throws IOException {
            Files.createFile(path("a"));
            Files.createFile(path("b"));
            Files.createSymbolicLink(path("link"), path("a"));

            assertThat(Files.isSameFile(path("a"), path("link"))).isTrue();
            assertThat(Files.isSameFile(path("a"), path("b"))).isFalse();
            // Two equal paths, rather than one path twice: isSameFile compares them before looking for the file.
            Path missing = path("missing");
            assertThat(Files.isSameFile(missing, path("missing"))).isTrue();
            assertThat(Files.isSameFile(path("a"), Path.of("a"))).isFalse();
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> Files.isSameFile(path("a"), path("missing")));
        }

        @Test
        void hidesDotFilesOnUnix() throws IOException {
            Files.createFile(path(".hidden"));

            assertThat(Files.isHidden(path(".hidden"))).isTrue();
            assertThat(Files.isHidden(path("/work"))).isFalse();
            assertThat(Files.isHidden(path("/"))).isFalse();
        }

        @Test
        void hidesFilesMarkedHiddenOnWindows() throws IOException {
            try (FileSystem windows = MemoryFileSystems.newFileSystem(Configuration.windows())) {
                Path file = Files.createFile(windows.getPath(".dot"));

                assertThat(Files.isHidden(file)).isFalse();
                Files.setAttribute(file, "dos:hidden", true);
                assertThat(Files.isHidden(file)).isTrue();
            }
        }

        @Test
        void checksAccessWithoutEnforcingIt() throws IOException {
            Path file = Files.createFile(path("file"));
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));

            fileSystem.provider().checkAccess(file, AccessMode.READ, AccessMode.WRITE, AccessMode.EXECUTE);

            assertThat(Files.isReadable(file)).isTrue();
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> fileSystem.provider().checkAccess(path("missing")));
        }

        @Test
        void findsTheFileStore() throws IOException {
            var store = Files.getFileStore(path("/work"));

            assertThat(store.type()).isEqualTo("memory");
            assertThat(store.isReadOnly()).isFalse();
            assertThatExceptionOfType(NoSuchFileException.class).isThrownBy(() -> Files.getFileStore(path("missing")));
        }
    }
}
