package be.imgn.gaia.memfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class NameMatchingTest {

    private static final String COMPOSED = "caf\u00e9";
    private static final String DECOMPOSED = "cafe\u0301";

    private static FileSystem fileSystem(NameMatching matching, Normalizer.@Nullable Form form) {
        return MemoryFileSystems.newFileSystem(Configuration.unix().toBuilder()
                .nameMatching(matching)
                .unicodeNormalization(form)
                .build());
    }

    private static String listed(Path directory) throws IOException {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> entry.getFileName().toString())
                    .findFirst()
                    .orElseThrow();
        }
    }

    @Test
    void matchesExactlyOnUnix() throws IOException {
        try (FileSystem unix = MemoryFileSystems.newFileSystem()) {
            Files.createFile(unix.getPath("File"));

            assertThat(Files.exists(unix.getPath("file"))).isFalse();
            Files.createFile(unix.getPath("file"));
            Files.createFile(unix.getPath(DECOMPOSED));
            assertThat(Files.exists(unix.getPath(COMPOSED))).isFalse();
        }
    }

    @Test
    void ignoresAsciiCase() throws IOException {
        try (FileSystem ascii = fileSystem(NameMatching.IGNORE_CASE_ASCII, null)) {
            Files.createFile(ascii.getPath("README.md"));
            Files.createFile(ascii.getPath("\u00c9t\u00e9"));

            assertThat(Files.exists(ascii.getPath("readme.MD"))).isTrue();
            assertThat(Files.exists(ascii.getPath("\u00e9t\u00e9"))).isFalse();
            assertThat(Files.exists(ascii.getPath("\u00c9T\u00c9"))).isFalse();
            assertThatExceptionOfType(FileAlreadyExistsException.class)
                    .isThrownBy(() -> Files.createFile(ascii.getPath("Readme.md")));
        }
    }

    @Test
    void ignoresUnicodeCaseAndKeepsNamesAsCreated() throws IOException {
        try (FileSystem windows = MemoryFileSystems.newFileSystem(Configuration.windows())) {
            Files.createFile(windows.getPath("\u00c9t\u00e9.TXT"));

            assertThat(Files.exists(windows.getPath("\u00e9T\u00c9.txt"))).isTrue();
            assertThat(listed(windows.getPath("C:\\work"))).isEqualTo("\u00c9t\u00e9.TXT");
            assertThat(windows.getPath("\u00e9t\u00e9.txt").toRealPath()).hasToString("C:\\work\\\u00c9t\u00e9.TXT");
        }
    }

    @Test
    void foldsTheCaseOfEachLetterAlone() throws IOException {
        try (FileSystem windows = MemoryFileSystems.newFileSystem(Configuration.windows())) {
            Files.createFile(windows.getPath("stra\u00dfe"));

            assertThat(Files.exists(windows.getPath("STRA\u00dfE"))).isTrue();
            assertThat(Files.exists(windows.getPath("strasse"))).isFalse();
            assertThat(Files.exists(windows.getPath("\ufb01le"))).isFalse();
        }
    }

    @Test
    void matchesNamesUnderANormalization() throws IOException {
        try (FileSystem macOS = MemoryFileSystems.newFileSystem(Configuration.macOS())) {
            Files.writeString(macOS.getPath(COMPOSED), "content");

            assertThat(Files.readString(macOS.getPath(DECOMPOSED))).isEqualTo("content");
            assertThat(Files.readString(macOS.getPath("CAF\u00c9"))).isEqualTo("content");
            assertThat(listed(macOS.getPath("/work"))).isEqualTo(COMPOSED);
        }
        try (FileSystem normalized = fileSystem(NameMatching.EXACT, Normalizer.Form.NFC)) {
            Files.createFile(normalized.getPath(DECOMPOSED));

            assertThat(Files.exists(normalized.getPath(COMPOSED))).isTrue();
            assertThat(Files.exists(normalized.getPath("CAF\u00c9"))).isFalse();
        }
    }
}
