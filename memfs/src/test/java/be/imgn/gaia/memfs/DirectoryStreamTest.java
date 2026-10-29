package be.imgn.gaia.memfs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.ClosedDirectoryStreamException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ProviderMismatchException;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DirectoryStreamTest {

    private final FileSystem fileSystem = MemoryFileSystems.newFileSystem();

    @BeforeEach
    void createFiles() throws IOException {
        Files.createDirectories(path("dir/sub"));
        Files.writeString(path("dir/a.txt"), "a");
        Files.writeString(path("dir/b.java"), "b");
    }

    @AfterEach
    void close() throws IOException {
        fileSystem.close();
    }

    private Path path(String path) {
        return fileSystem.getPath(path);
    }

    // Through the provider: a stream these tests close by hand, or later than where it is opened.
    private static DirectoryStream<Path> open(Path directory) throws IOException {
        return directory.getFileSystem().provider().newDirectoryStream(directory, entry -> true);
    }

    private static SecureDirectoryStream<Path> secure(Path directory) throws IOException {
        return (SecureDirectoryStream<Path>) open(directory);
    }

    private static List<String> names(Iterable<Path> paths) {
        List<String> names = new ArrayList<>();
        paths.forEach(path -> names.add(path.toString()));
        return names;
    }

    @Nested
    class Listing {

        @Test
        void listsEntriesResolvedAgainstTheDirectory() throws IOException {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path("dir"))) {
                assertThat(names(stream)).containsExactly("dir/sub", "dir/a.txt", "dir/b.java");
            }
            try (Stream<Path> list = Files.list(path("/work/dir"))) {
                assertThat(list.map(Path::toString)).contains("/work/dir/sub");
            }
        }

        @Test
        void filters() throws IOException {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path("dir"), "*.{txt,md}")) {
                assertThat(names(stream)).containsExactly("dir/a.txt");
            }
            try (DirectoryStream<Path> stream =
                    Files.newDirectoryStream(path("dir"), entry -> Files.isDirectory(entry))) {
                assertThat(names(stream)).containsExactly("dir/sub");
            }
        }

        @Test
        void wrapsFailuresOfTheFilter() throws IOException {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path("dir"), entry -> {
                throw new IOException("filter failed");
            })) {
                Iterator<Path> iterator = stream.iterator();

                assertThatExceptionOfType(DirectoryIteratorException.class)
                        .isThrownBy(iterator::hasNext)
                        .withCauseInstanceOf(IOException.class);
            }
        }

        @Test
        void walksTrees() throws IOException {
            try (Stream<Path> walk = Files.walk(path("dir"))) {
                assertThat(walk.map(Path::toString))
                        .containsExactlyInAnyOrder("dir", "dir/sub", "dir/a.txt", "dir/b.java");
            }
            try (Stream<Path> found = Files.find(path("/"), 10, (entry, attributes) -> attributes.isRegularFile())) {
                assertThat(found.map(Path::toString)).containsExactlyInAnyOrder("/work/dir/a.txt", "/work/dir/b.java");
            }
        }

        @Test
        void givesOneIteratorOnly() throws IOException {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path("dir"))) {
                Iterator<Path> iterator = stream.iterator();

                assertThatIllegalStateException().isThrownBy(stream::iterator);
                iterator.next();
                iterator.next();
                iterator.next();
                assertThat(iterator.hasNext()).isFalse();
                assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(iterator::next);
            }
        }

        @Test
        void endsOnceClosed() throws IOException {
            DirectoryStream<Path> stream = open(path("dir"));
            Iterator<Path> iterator = stream.iterator();

            stream.close();

            assertThat(iterator.hasNext()).isFalse();
            assertThatIllegalStateException().isThrownBy(stream::iterator);
        }

        @Test
        void needsADirectory() {
            assertThatExceptionOfType(NotDirectoryException.class)
                    .isThrownBy(() -> Files.newDirectoryStream(path("dir/a.txt")))
                    .withMessage("dir/a.txt");
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> Files.newDirectoryStream(path("missing")));
        }
    }

    @Nested
    class Secure {

        @Test
        void isSecureWithTheFeature() throws IOException {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path("dir"))) {
                assertThat(stream).isInstanceOf(SecureDirectoryStream.class);
            }
            try (FileSystem plain = MemoryFileSystems.newFileSystem(
                            Configuration.unix().toBuilder().features().build());
                    DirectoryStream<Path> stream = Files.newDirectoryStream(plain.getPath("/"))) {
                assertThat(stream).isNotInstanceOf(SecureDirectoryStream.class);
            }
        }

        @Test
        void resolvesTheEntriesOfAStreamItOpensAgainstItsOwnPath() throws IOException {
            Files.writeString(path("dir/sub/x.txt"), "x");
            try (SecureDirectoryStream<Path> stream = secure(path("dir"));
                    SecureDirectoryStream<Path> sub = stream.newDirectoryStream(path("sub"))) {
                assertThat(names(sub)).containsExactly("dir/sub/x.txt");
            }
        }

        @Test
        void worksRelativeToItsDirectoryWhereverItMoves() throws IOException {
            try (SecureDirectoryStream<Path> stream = secure(path("dir"))) {
                Files.move(path("dir"), path("/elsewhere"));

                try (SeekableByteChannel channel = stream.newByteChannel(
                        path("new.txt"), Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE))) {
                    channel.write(ByteBuffer.wrap("x".getBytes(UTF_8)));
                }
                stream.deleteFile(path("a.txt"));
                stream.deleteDirectory(path("sub"));
                try (SecureDirectoryStream<Path> nested = stream.newDirectoryStream(path("."))) {
                    assertThat(names(nested)).containsExactly("dir/./b.java", "dir/./new.txt");
                }
            }

            assertThat(Files.readString(path("/elsewhere/new.txt"))).isEqualTo("x");
            assertThat(Files.exists(path("/elsewhere/a.txt"))).isFalse();
            assertThat(Files.exists(path("/elsewhere/sub"))).isFalse();
        }

        @Test
        void movesBetweenDirectories() throws IOException {
            Files.createDirectory(path("target"));
            try (SecureDirectoryStream<Path> source = secure(path("dir"));
                    SecureDirectoryStream<Path> target = secure(path("target"))) {
                source.move(path("a.txt"), target, path("moved.txt"));
            }
            try (FileSystem other = MemoryFileSystems.newFileSystem();
                    SecureDirectoryStream<Path> source = secure(path("dir"));
                    SecureDirectoryStream<Path> foreign = secure(other.getPath("/"))) {
                assertThatExceptionOfType(ProviderMismatchException.class)
                        .isThrownBy(() -> source.move(path("b.java"), foreign, other.getPath("b.java")));
            }

            assertThat(Files.readString(path("target/moved.txt"))).isEqualTo("a");
        }

        @Test
        void readsAttributesOfTheDirectoryAndItsEntries() throws IOException {
            try (SecureDirectoryStream<Path> stream = secure(path("dir"))) {
                BasicFileAttributeView directory = stream.getFileAttributeView(BasicFileAttributeView.class);
                PosixFileAttributeView entry = stream.getFileAttributeView(path("a.txt"), PosixFileAttributeView.class);

                assertThat(directory.readAttributes().isDirectory()).isTrue();
                assertThat(entry.readAttributes().size()).isEqualTo(1);
                assertThat(stream.getFileAttributeView(DosFileAttributeView.class))
                        .isNull();
            }
        }

        @Test
        void refusesWhatItCannotDo() throws IOException {
            SecureDirectoryStream<Path> stream = secure(path("dir"));

            assertThatExceptionOfType(FileSystemException.class)
                    .isThrownBy(() -> stream.deleteFile(path("sub")))
                    .withMessageEndingWith("Is a directory");
            assertThatExceptionOfType(NotDirectoryException.class)
                    .isThrownBy(() -> stream.deleteDirectory(path("a.txt")));
            assertThatExceptionOfType(ProviderMismatchException.class)
                    .isThrownBy(() -> stream.deleteFile(Path.of("a.txt")));
            stream.close();
            assertThatExceptionOfType(ClosedDirectoryStreamException.class)
                    .isThrownBy(() -> stream.deleteFile(path("a.txt")));
            assertThatExceptionOfType(ClosedDirectoryStreamException.class)
                    .isThrownBy(() -> stream.getFileAttributeView(BasicFileAttributeView.class));
        }
    }

    @Nested
    class Matching {

        private boolean matches(String pattern, String path) {
            PathMatcher matcher = fileSystem.getPathMatcher(pattern);
            return matcher.matches(path(path));
        }

        @Test
        void matchesGlobs() {
            assertThat(matches("glob:*.txt", "a.txt")).isTrue();
            assertThat(matches("glob:*.txt", "dir/a.txt")).isFalse();
            assertThat(matches("glob:**/*.txt", "dir/sub/a.txt")).isTrue();
            assertThat(matches("glob:?.txt", "ab.txt")).isFalse();
            assertThat(matches("glob:[ab].txt", "b.txt")).isTrue();
            assertThat(matches("glob:[!ab].txt", "b.txt")).isFalse();
            assertThat(matches("glob:[a-c].txt", "c.txt")).isTrue();
            assertThat(matches("glob:*.{java,txt}", "a.java")).isTrue();
            assertThat(matches("glob:\\*.txt", "*.txt")).isTrue();
            assertThat(matches("glob:a.(txt)", "a.(txt)")).isTrue();
            assertThat(matches("glob:/work/*", "/work/a")).isTrue();
            assertThat(matches("GLOB:*.TXT", "a.txt")).isFalse();
        }

        @Test
        void matchesSpecialCharactersLiterally() {
            assertThat(matches("glob:a}b,c", "a}b,c")).isTrue();
            assertThat(matches("glob:[[\\&^]x", "&x")).isTrue();
            assertThat(matches("glob:[]a]", "]")).isTrue();
            assertThat(matches("glob:a+b", "a+b")).isTrue();
        }

        @Test
        void matchesRegularExpressions() {
            assertThat(matches("regex:[a-z]+\\.txt", "abc.txt")).isTrue();
            assertThat(matches("regex:.*\\.java", "dir/b.java")).isTrue();
        }

        @Test
        void ignoresCaseOnWindowsAndCaseInsensitiveFileSystems() throws IOException {
            try (FileSystem windows = MemoryFileSystems.newFileSystem(Configuration.windows());
                    FileSystem macOS = MemoryFileSystems.newFileSystem(Configuration.macOS())) {
                assertThat(windows.getPathMatcher("glob:*.TXT").matches(windows.getPath("a.txt")))
                        .isTrue();
                assertThat(windows.getPathMatcher("glob:C:/dir/*").matches(windows.getPath("C:\\dir\\a")))
                        .isTrue();
                assertThat(windows.getPathMatcher("glob:C:\\\\dir\\\\*").matches(windows.getPath("C:\\dir\\a")))
                        .isTrue();
                assertThat(windows.getPathMatcher("glob:*").matches(windows.getPath("dir\\a")))
                        .isFalse();
                assertThat(macOS.getPathMatcher("glob:*.TXT").matches(macOS.getPath("a.txt")))
                        .isTrue();
            }
        }

        @Test
        void refusesBadPatterns() {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .isThrownBy(() -> fileSystem.getPathMatcher("*.txt"));
            assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> fileSystem.getPathMatcher(":x"));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> fileSystem.getPathMatcher("wildcard:*"));
            for (String bad : List.of("glob:[ab", "glob:{a,{b}}", "glob:{a", "glob:a\\", "regex:(")) {
                assertThatExceptionOfType(PatternSyntaxException.class)
                        .as(bad)
                        .isThrownBy(() -> fileSystem.getPathMatcher(bad));
            }
        }
    }
}
