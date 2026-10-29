package be.imgn.gaia.memfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PathTest {

    @Nested
    class Unix {

        private final FileSystem fileSystem = MemoryFileSystems.newFileSystem();

        @AfterEach
        void close() throws IOException {
            fileSystem.close();
        }

        private Path path(String first, String... more) {
            return fileSystem.getPath(first, more);
        }

        @Test
        void parsesRootsAndNames() {
            Path path = path("/foo//bar/", "baz");

            assertThat(path).hasToString("/foo/bar/baz");
            assertThat(path.isAbsolute()).isTrue();
            assertThat(path.getRoot()).hasToString("/");
            assertThat(path.getFileName()).hasToString("baz");
            assertThat(path.getParent()).hasToString("/foo/bar");
            assertThat(path.getNameCount()).isEqualTo(3);
            assertThat(path.getName(1)).hasToString("bar");
            assertThat(path.subpath(1, 3)).hasToString("bar/baz");
            List<String> names = new ArrayList<>();
            path.forEach(name -> names.add(name.toString()));
            assertThat(names).containsExactly("foo", "bar", "baz");
            assertThat(path("foo", "", "bar")).hasToString("foo/bar");
        }

        @Test
        void hasTheEmptyPathAndTheRoot() {
            Path empty = path("");
            Path root = path("/");

            assertThat(empty).hasToString("");
            assertThat(empty.getNameCount()).isEqualTo(1);
            assertThat(empty.getFileName()).isEqualTo(empty);
            assertThat(empty.getParent()).isNull();
            assertThat(empty.getRoot()).isNull();
            assertThat(empty.isAbsolute()).isFalse();
            assertThat(root.getNameCount()).isZero();
            assertThat(root.getFileName()).isNull();
            assertThat(root.getParent()).isNull();
            assertThat(path("foo").getParent()).isNull();
            assertThat(path("/foo").getParent()).isEqualTo(root);
        }

        @Test
        void rejectsNulCharacters() {
            assertThatExceptionOfType(InvalidPathException.class)
                    .isThrownBy(() -> path("foo\0bar"))
                    .withMessageContaining("Nul character not allowed");
        }

        @Test
        void rejectsInvalidNameIndexes() {
            Path path = path("/a/b");

            assertThatIllegalArgumentException().isThrownBy(() -> path.getName(-1));
            assertThatIllegalArgumentException().isThrownBy(() -> path.getName(2));
            assertThatIllegalArgumentException().isThrownBy(() -> path.subpath(1, 1));
            assertThatIllegalArgumentException().isThrownBy(() -> path.subpath(0, 3));
            assertThatIllegalArgumentException().isThrownBy(() -> path.subpath(-1, 1));
        }

        @Test
        void normalizes() {
            assertThat(path("/a/./b/../c").normalize()).hasToString("/a/c");
            assertThat(path("/../a").normalize()).hasToString("/a");
            assertThat(path("../a/../../b").normalize()).hasToString("../../b");
            assertThat(path("a/..").normalize()).hasToString("");
            assertThat(path(".").normalize()).hasToString("");
        }

        @Test
        void resolves() {
            Path base = path("/a/b");

            assertThat(base.resolve("c/d")).hasToString("/a/b/c/d");
            assertThat(base.resolve("/x")).hasToString("/x");
            assertThat(base.resolve("")).isEqualTo(base);
            assertThat(path("").resolve("c")).hasToString("c");
            assertThat(base.resolveSibling("c")).hasToString("/a/c");
            assertThat(path("a").resolve(path("b"))).hasToString("a/b");
        }

        @Test
        void relativizes() {
            assertThat(path("/a/b").relativize(path("/a/b/c/d"))).hasToString("c/d");
            assertThat(path("/a/b/c").relativize(path("/a/x"))).hasToString("../../x");
            assertThat(path("/a").relativize(path("/a"))).hasToString("");
            assertThat(path("a").relativize(path("b"))).hasToString("../b");
            assertThat(path("").relativize(path("a/b"))).hasToString("a/b");
            assertThat(path("a/b").relativize(path(""))).hasToString("../..");
            assertThatIllegalArgumentException().isThrownBy(() -> path("/a").relativize(path("b")));
        }

        @Test
        void comparesNamesExactly() {
            assertThat(path("/a/b").startsWith(path("/a"))).isTrue();
            assertThat(path("/a/b").startsWith("/A")).isFalse();
            assertThat(path("/a/b").startsWith("a")).isFalse();
            assertThat(path("a/b").startsWith("")).isFalse();
            assertThat(path("").startsWith("")).isTrue();
            assertThat(path("/a/b").endsWith("b")).isTrue();
            assertThat(path("/a/b").endsWith("a/b")).isTrue();
            assertThat(path("/a/b").endsWith("/a/b")).isTrue();
            assertThat(path("/a/b").endsWith("/b")).isFalse();
            assertThat(path("/a/b").endsWith("c/a/b")).isFalse();
            assertThat(path("/a/b").endsWith("")).isFalse();
            assertThat(path("").endsWith("")).isTrue();
            assertThat(path("/a")).isNotEqualTo(path("/A"));
            assertThat(path("/a")).isEqualTo(path("/a/")).hasSameHashCodeAs(path("/a"));
            assertThat(path("/a").compareTo(path("/b"))).isNegative();
        }

        @Test
        void makesAbsoluteAndRealPaths() throws IOException {
            Files.createDirectories(path("/work/a/b"));
            Files.createSymbolicLink(path("/link"), path("/work/a"));

            assertThat(path("a").toAbsolutePath()).hasToString("/work/a");
            assertThat(path("/x").toAbsolutePath()).hasToString("/x");
            assertThat(path("a/./b/..").toRealPath()).hasToString("/work/a");
            assertThat(path("/link/b").toRealPath()).hasToString("/work/a/b");
            assertThat(path("/link").toRealPath(LinkOption.NOFOLLOW_LINKS)).hasToString("/link");
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> path("missing").toRealPath());
        }

        @Test
        void convertsToAndFromUris() throws IOException {
            Files.createDirectories(path("/dir"));
            Path file = path("/dir/hello world.txt");

            URI uri = file.toUri();
            URI directory = path("/dir").toUri();

            assertThat(uri.getScheme()).isEqualTo("mem");
            assertThat(uri.getPath()).isEqualTo("/dir/hello world.txt");
            assertThat(directory.getPath()).isEqualTo("/dir/");
            assertThat(Path.of(uri)).isEqualTo(file);
            assertThat(path("relative").toUri().getPath()).isEqualTo("/work/relative");
        }

        @Test
        void rejectsPathsOfOtherFileSystems() throws IOException {
            try (FileSystem other = MemoryFileSystems.newFileSystem()) {
                Path foreign = other.getPath("/a");

                assertThatExceptionOfType(ProviderMismatchException.class)
                        .isThrownBy(() -> path("/a").resolve(foreign));
                assertThatExceptionOfType(ProviderMismatchException.class)
                        .isThrownBy(() -> path("/a").startsWith(Path.of("/a")));
                assertThat(path("/a")).isNotEqualTo(foreign);
            }
        }

        @Test
        void cannotBeFiles() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> path("/a").toFile());
        }
    }

    @Nested
    class Windows {

        private final FileSystem fileSystem = MemoryFileSystems.newFileSystem(Configuration.windows().toBuilder()
                .roots("C:\\", "D:\\", "\\\\server\\share\\")
                .build());

        @AfterEach
        void close() throws IOException {
            fileSystem.close();
        }

        private Path path(String first, String... more) {
            return fileSystem.getPath(first, more);
        }

        @Test
        void parsesDrivesSharesAndBothSeparators() {
            assertThat(path("C:/foo\\bar")).hasToString("C:\\foo\\bar");
            assertThat(path("C:\\foo").getRoot()).hasToString("C:\\");
            assertThat(path("C:\\foo").isAbsolute()).isTrue();
            assertThat(path("C:foo").getRoot()).hasToString("C:");
            assertThat(path("C:foo").isAbsolute()).isFalse();
            assertThat(path("\\foo").getRoot()).hasToString("\\");
            assertThat(path("\\foo").isAbsolute()).isFalse();
            assertThat(path("//server/share/dir").getRoot()).hasToString("\\\\server\\share\\");
            assertThat(path("\\\\server\\share\\dir").isAbsolute()).isTrue();
            assertThat(path("foo", "bar")).hasToString("foo\\bar");
            assertThat(fileSystem.getSeparator()).isEqualTo("\\");
        }

        @Test
        void rejectsReservedCharactersAndTrailingSpaces() {
            for (String invalid :
                    List.of("a<b", "a>b", "dir\\a:b", "a\"b", "a|b", "a?b", "a*b", "a\u0001b", "dir\\name ")) {
                assertThatExceptionOfType(InvalidPathException.class)
                        .as(invalid)
                        .isThrownBy(() -> path(invalid));
            }
            assertThatExceptionOfType(InvalidPathException.class)
                    .isThrownBy(() -> path("\\\\server"))
                    .withMessageContaining("UNC path is missing sharename");
            assertThatExceptionOfType(InvalidPathException.class)
                    .isThrownBy(() -> path("\\\\\\share"))
                    .withMessageContaining("UNC path is missing hostname");
        }

        @Test
        void comparesIgnoringCase() {
            assertThat(path("C:\\Foo\\BAR")).isEqualTo(path("c:/foo/bar")).hasSameHashCodeAs(path("c:/foo/bar"));
            assertThat(path("C:\\Foo\\Bar").startsWith("c:\\FOO")).isTrue();
            assertThat(path("C:\\Foo\\Bar").endsWith("bar")).isTrue();
            assertThat(path("C:\\a").compareTo(path("c:\\A"))).isZero();
            assertThat(path("C:\\a\\b").relativize(path("c:\\A\\c"))).hasToString("..\\c");
        }

        @Test
        void hashesAsItCompares() {
            // The long s and the dotted capital I match "s" and "i" letter by letter, not as whole strings.
            assertThat(path("C:\\\u017f")).isEqualTo(path("C:\\s")).hasSameHashCodeAs(path("C:\\s"));
            assertThat(path("C:\\\u0130")).isEqualTo(path("C:\\i")).hasSameHashCodeAs(path("C:\\i"));
            assertThat(Set.of(path("C:\\\u017f"))).contains(path("c:\\S"));
        }

        @Test
        void resolvesRootedRelativePaths() {
            assertThat(path("D:\\dir").resolve(path("\\other"))).hasToString("D:\\other");
            assertThat(path("D:\\dir").resolve(path("d:sub"))).hasToString("D:\\dir\\sub");
            assertThat(path("D:\\dir").resolve(path("C:sub"))).hasToString("C:sub");
            assertThat(path("dir").resolve(path("\\other"))).hasToString("\\other");
            assertThat(path("\\other").toAbsolutePath()).hasToString("C:\\other");
            assertThat(path("C:sub").toAbsolutePath()).hasToString("C:\\work\\sub");
        }

        @Test
        void makesDriveRelativePathsAbsolute() throws IOException {
            Path absolute = path("D:dir").toAbsolutePath();

            assertThat(absolute).hasToString("D:\\dir");
            assertThat(absolute.isAbsolute()).isTrue();
            Files.createDirectory(absolute);
            assertThat(Path.of(path("D:dir").toUri())).isEqualTo(absolute);
        }

        @Test
        void resolvesRootRelativePathsOnTheShareOfTheWorkingDirectory() throws IOException {
            try (FileSystem share = MemoryFileSystems.newFileSystem(Configuration.windows().toBuilder()
                    .roots("C:\\", "\\\\server\\share\\")
                    .workingDirectory("\\\\server\\share\\work")
                    .build())) {
                Path path = share.getPath("\\dir");

                assertThat(share.getPath("\\\\server\\share\\x").resolve(path)).hasToString("\\\\server\\share\\dir");
                assertThat(path.toAbsolutePath()).hasToString("\\\\server\\share\\dir");
                Files.createDirectory(path);
                assertThat(Files.isDirectory(share.getPath("\\\\server\\share\\dir")))
                        .isTrue();
            }
        }

        @Test
        void findsFilesThroughRootedRelativePaths() throws IOException {
            Files.createDirectories(path("D:\\data"));
            Files.writeString(path("D:\\data\\file.txt"), "text");
            Files.writeString(path("C:\\top.txt"), "top");

            assertThat(Files.readString(path("D:data\\file.txt"))).isEqualTo("text");
            assertThat(Files.readString(path("\\top.txt"))).isEqualTo("top");
            assertThat(Files.exists(path("C:top.txt"))).isFalse();
        }

        @Test
        void convertsToAndFromUris() throws IOException {
            Files.createDirectories(path("D:\\dir"));
            Files.createDirectories(path("\\\\server\\share\\docs"));

            URI drive = path("D:\\dir\\file.txt").toUri();
            URI share = path("\\\\server\\share\\docs").toUri();

            assertThat(drive.getPath()).isEqualTo("/D:/dir/file.txt");
            assertThat(share.getPath()).isEqualTo("///server/share/docs/");
            assertThat(Path.of(drive)).isEqualTo(path("D:\\dir\\file.txt"));
            assertThat(Path.of(share)).isEqualTo(path("\\\\server\\share\\docs"));
            assertThat(FileSystems.getFileSystem(drive)).isSameAs(fileSystem);
        }

        @Test
        void hasTheEmptyPathAndRefusesRelativeUris() {
            assertThat(path("")).hasToString("");
            assertThat(path("").getNameCount()).isEqualTo(1);
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> fileSystem.provider().getPath(URI.create(fileSystem + "/relative")))
                    .withMessageContaining("Not an absolute path");
        }

        @Test
        void listsItsRoots() {
            assertThat(fileSystem.getRootDirectories())
                    .extracting(Path::toString)
                    .containsExactly("C:\\", "D:\\", "\\\\server\\share\\");
        }
    }
}
