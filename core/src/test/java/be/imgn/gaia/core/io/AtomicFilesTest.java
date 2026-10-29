package be.imgn.gaia.core.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFilesTest {

    @TempDir
    Path directory;

    private static void assumePosix() {
        assumeThat(FileSystems.getDefault().supportedFileAttributeViews()).contains("posix");
    }

    private List<Path> entries() throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.toList();
        }
    }

    @Test
    void createsAFile() throws IOException {
        Path file = directory.resolve("settings.toml");

        AtomicFiles.write(file, new byte[] {1, 2, 3});

        assertThat(file).hasBinaryContent(new byte[] {1, 2, 3});
    }

    @Test
    void replacesTheContentOfAFile() throws IOException {
        Path file = Files.writeString(directory.resolve("settings.toml"), "a much longer previous content");

        AtomicFiles.writeString(file, "new");

        assertThat(file).hasContent("new");
    }

    @Test
    void leavesNoTemporaryFileBehind() throws IOException {
        Path file = directory.resolve("settings.toml");

        AtomicFiles.writeString(file, "first");
        AtomicFiles.writeString(file, "second");

        assertThat(entries()).containsExactly(file);
    }

    @Test
    void writesAFileWithALongName() throws IOException {
        Path file = directory.resolve("a".repeat(240));

        AtomicFiles.writeString(file, "new");

        assertThat(file).hasContent("new");
        assertThat(entries()).containsExactly(file);
    }

    @Test
    void writesTextInUtf8ByDefault() throws IOException {
        Path file = directory.resolve("text");

        AtomicFiles.writeString(file, "café ✓");

        assertThat(file).hasBinaryContent("café ✓".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void writesTextInTheGivenCharset() throws IOException {
        Path file = directory.resolve("text");

        AtomicFiles.writeString(file, "café", StandardCharsets.ISO_8859_1);

        assertThat(file).hasBinaryContent("café".getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void rejectsTextTheCharsetCannotEncodeAndKeepsTheFile() throws IOException {
        Path file = Files.writeString(directory.resolve("text"), "previous");

        assertThatExceptionOfType(CharacterCodingException.class)
                .isThrownBy(() -> AtomicFiles.writeString(file, "✓", StandardCharsets.US_ASCII));

        assertThat(file).hasContent("previous");
        assertThat(entries()).containsExactly(file);
    }

    @Test
    void writesWhatTheOutputWrites() throws IOException {
        Path file = directory.resolve("export.bin");

        AtomicFiles.write(file, out -> {
            out.write(1);
            out.write(new byte[] {2, 3, 4}, 1, 2);
        });

        assertThat(file).hasBinaryContent(new byte[] {1, 3, 4});
    }

    @Test
    void letsTheOutputCloseItsStream() throws IOException {
        Path file = directory.resolve("settings.toml");

        AtomicFiles.write(file, out -> {
            try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                writer.write("closed");
            }
        });

        assertThat(file).hasContent("closed");
    }

    @Test
    void keepsTheFileAndRemovesTheTemporaryOneWhenTheOutputFails() throws IOException {
        Path file = Files.writeString(directory.resolve("settings.toml"), "previous");

        assertThatIOException()
                .isThrownBy(() -> AtomicFiles.write(file, out -> {
                    out.write(new byte[] {1, 2, 3});
                    throw new IOException("disk full");
                }))
                .withMessage("disk full");

        assertThat(file).hasContent("previous");
        assertThat(entries()).containsExactly(file);
    }

    @Test
    void removesTheTemporaryFileWhenTheOutputThrowsAnUncheckedException() throws IOException {
        Path file = directory.resolve("settings.toml");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> AtomicFiles.write(file, out -> {
                    throw new IllegalStateException("bug");
                }));

        assertThat(entries()).isEmpty();
    }

    @Test
    void failsInADirectoryThatDoesNotExist() {
        Path file = directory.resolve("missing").resolve("settings.toml");

        assertThatExceptionOfType(NoSuchFileException.class).isThrownBy(() -> AtomicFiles.writeString(file, "text"));
    }

    @Test
    void refusesToReplaceADirectory() throws IOException {
        Path subdirectory = Files.createDirectory(directory.resolve("settings"));

        assertThatExceptionOfType(FileSystemException.class)
                .isThrownBy(() -> AtomicFiles.writeString(subdirectory, "text"))
                .withMessageContaining("Is a directory");

        assertThat(subdirectory).isEmptyDirectory();
    }

    @Test
    void keepsThePermissionsOfTheFile() throws IOException {
        assumePosix();
        Path file = Files.writeString(directory.resolve("settings.toml"), "previous");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));

        AtomicFiles.writeString(file, "new");

        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-r-----"));
    }

    @Test
    void keepsThePermissionsTheUmaskRemoves() throws IOException {
        assumePosix();
        Path file = Files.writeString(directory.resolve("settings.toml"), "previous");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-rw-rw-"));

        AtomicFiles.writeString(file, "new");

        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-rw-rw-"));
    }

    @Test
    void neverGivesTheTemporaryFileMorePermissionsThanTheFile() throws IOException {
        assumePosix();
        Path file = Files.writeString(directory.resolve("secret"), "previous");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        var temporaryPermissions = new ArrayList<Set<PosixFilePermission>>();

        AtomicFiles.write(file, out -> {
            for (Path entry : entries()) {
                if (!entry.equals(file)) {
                    temporaryPermissions.add(Files.getPosixFilePermissions(entry, LinkOption.NOFOLLOW_LINKS));
                }
            }
            out.write(new byte[] {1, 2, 3});
        });

        assertThat(temporaryPermissions)
                .singleElement()
                .satisfies(permissions ->
                        assertThat(permissions).isSubsetOf(PosixFilePermissions.fromString("rw-------")));
    }

    @Test
    void replacesAReadOnlyFile() throws IOException {
        assumePosix();
        Path file = Files.writeString(directory.resolve("settings.toml"), "previous");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--------"));

        AtomicFiles.writeString(file, "new");

        assertThat(file).hasContent("new");
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("r--------"));
    }

    @Test
    void replacesAWriteOnlyFileWhosePermissionsTheUmaskRemoves() throws IOException {
        assumePosix();
        Path file = Files.writeString(directory.resolve("log"), "previous");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("-w--w--w-"));

        AtomicFiles.writeString(file, "new");

        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("-w--w--w-"));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        assertThat(file).hasContent("new");
    }

    @Test
    void givesANewFileTheDefaultPermissions() throws IOException {
        assumePosix();
        Path file = directory.resolve("settings.toml");
        Path reference = Files.createFile(directory.resolve("reference"));

        AtomicFiles.writeString(file, "new");

        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(Files.getPosixFilePermissions(reference));
    }

    @Test
    void replacesTheFileASymbolicLinkPointsTo() throws IOException {
        Path target = Files.writeString(directory.resolve("dotfiles-settings.toml"), "previous");
        Path link = Files.createSymbolicLink(directory.resolve("settings.toml"), target.getFileName());

        AtomicFiles.writeString(link, "new");

        assertThat(link).isSymbolicLink();
        assertThat(target).hasContent("new");
    }

    @Test
    void replacesASymbolicLinkToAFileThatDoesNotExist() throws IOException {
        Path target = directory.resolve("dotfiles-settings.toml");
        Path link = Files.createSymbolicLink(directory.resolve("settings.toml"), target.getFileName());

        AtomicFiles.writeString(link, "new");

        assertThat(link).isRegularFile().hasContent("new");
        assertThat(target).doesNotExist();
    }

    @Test
    void rejectsACharsetThatCannotEncode() {
        var file = directory.resolve("text");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> AtomicFiles.writeString(file, "text", Charset.forName("ISO-2022-CN")))
                .withMessage("ISO-2022-CN does not support encoding");
        assertThat(file).doesNotExist();
    }
}
