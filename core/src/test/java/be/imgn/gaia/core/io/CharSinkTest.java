package be.imgn.gaia.core.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import be.imgn.gaia.core.base.Charsets;
import java.io.BufferedWriter;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CharSinkTest {

    private static final String SEPARATOR = System.lineSeparator();

    @TempDir
    Path directory;

    @Test
    void writesCharacters() throws IOException {
        var sink = new TrackedSink();

        sink.write(CharBuffer.wrap("text"));

        assertThat(sink.out).hasToString("text");
        assertThat(sink.closed).isEqualTo(1);
    }

    @Test
    void writesLinesEachFollowedByTheSystemSeparator() throws IOException {
        var sink = new TrackedSink();

        sink.writeLines(List.of("a", "b"));

        assertThat(sink.out).hasToString("a" + SEPARATOR + "b" + SEPARATOR);
        assertThat(sink.closed).isEqualTo(1);
    }

    @Test
    void writesLinesEachFollowedByASeparator() throws IOException {
        var sink = new TrackedSink();

        sink.writeLines(List.of("a", "b"), "\n");

        assertThat(sink.out).hasToString("a\nb\n");
    }

    @Test
    void writesTheLinesOfAnIterableThatCanBeIteratedOnlyOnce() throws IOException {
        var sink = new TrackedSink();
        var iterator = List.of("a", "b").iterator();
        Iterable<String> lines = () -> iterator;

        sink.writeLines(lines, "\n");

        assertThat(sink.out).hasToString("a\nb\n");
    }

    @Test
    void writesTheLinesOfAStream() throws IOException {
        var sink = new TrackedSink();

        sink.writeLines(Stream.of("a", "b"));
        sink.writeLines(Stream.of("c"), ";");

        assertThat(sink.out).hasToString("a" + SEPARATOR + "b" + SEPARATOR + "c;");
        assertThat(sink.closed).isEqualTo(2);
    }

    @Test
    void writesNothingForNoLines() throws IOException {
        var sink = new TrackedSink();

        sink.writeLines(List.of());

        assertThat(sink.out).hasToString("");
    }

    @Test
    void writesTheCharactersOfAReadableAndLeavesItOpen() throws IOException {
        var sink = new TrackedSink();
        var text = "aé😀".repeat(5_000);

        assertThat(sink.writeFrom(new StringReader(text))).isEqualTo(text.length());
        assertThat(sink.writeFrom(CharBuffer.wrap("!"))).isEqualTo(1);

        assertThat(sink.out).hasToString(text + "!");
    }

    @Test
    void opensABufferedStream() throws IOException {
        var sink = new TrackedSink();

        try (var out = sink.openBufferedStream()) {
            assertThat(out).isInstanceOf(BufferedWriter.class);
            out.write("text");
        }

        assertThat(sink.out).hasToString("text");
    }

    @Test
    void writesATextFile() throws IOException {
        var file = Files.writeString(directory.resolve("text"), "previous content");

        CharSink.of(file, StandardCharsets.ISO_8859_1).write("café");

        assertThat(file).hasBinaryContent("café".getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void writesATextFileInTheDefaultCharset() throws IOException {
        var file = Files.writeString(directory.resolve("text"), "previous content");

        CharSink.of(file).write("café ✓");

        assertThat(file).hasBinaryContent("café ✓".getBytes(Charsets.DEFAULT_CHARSET));
    }

    @Test
    void appendsToATextFileInTheDefaultCharset() throws IOException {
        var file = Files.writeString(directory.resolve("text"), "a", Charsets.DEFAULT_CHARSET);

        CharSink.of(file, StandardOpenOption.APPEND).write("✓");

        assertThat(file).hasBinaryContent("a✓".getBytes(Charsets.DEFAULT_CHARSET));
    }

    @Test
    void appendsToATextFile() throws IOException {
        var file = Files.writeString(directory.resolve("text"), "a");

        CharSink.of(file, StandardCharsets.UTF_8, StandardOpenOption.APPEND).write("b");

        assertThat(file).hasContent("ab");
    }

    @Test
    void decodesBytes() throws IOException {
        var sink = new TrackedSink();

        sink.asByteSink(StandardCharsets.UTF_8).write("café ✓ 😀".getBytes(StandardCharsets.UTF_8));

        assertThat(sink.out).hasToString("café ✓ 😀");
        assertThat(sink.closed).isEqualTo(1);
    }

    @Test
    void decodesBytesInTheDefaultCharset() throws IOException {
        var sink = new TrackedSink();

        sink.asByteSink().write("café ✓ 😀".getBytes(Charsets.DEFAULT_CHARSET));

        assertThat(sink.out).hasToString("café ✓ 😀");
        assertThat(sink.closed).isEqualTo(1);
    }

    @Test
    void decodesACharacterWrittenOneByteAtATime() throws IOException {
        var sink = new TrackedSink();

        try (var out = sink.asByteSink(StandardCharsets.UTF_8).openStream()) {
            for (var b : "😀".getBytes(StandardCharsets.UTF_8)) {
                out.write(b);
                out.flush();
            }
        }

        assertThat(sink.out).hasToString("😀");
    }

    @Test
    void decodesBytesBeyondABuffer() throws IOException {
        var sink = new TrackedSink();
        var text = "aé😀".repeat(10_000);

        sink.asByteSink(StandardCharsets.UTF_8)
                .writeFrom(
                        ByteSource.wrap(text.getBytes(StandardCharsets.UTF_8)).openStream());

        assertThat(sink.out).hasToString(text);
    }

    @Test
    void failsOnBytesThatAreNotValidInTheCharset() {
        var sink = new TrackedSink().asByteSink(StandardCharsets.UTF_8);

        assertThatExceptionOfType(MalformedInputException.class)
                .isThrownBy(() -> sink.write(new byte[] {'a', (byte) 0xFF, 'b'}));
    }

    @Test
    void writesTheCharactersBeforeInvalidBytesOnce() {
        var tracked = new TrackedSink();
        var sink = tracked.asByteSink(StandardCharsets.UTF_8);

        assertThatExceptionOfType(MalformedInputException.class)
                .isThrownBy(() -> sink.write(new byte[] {'a', 'b', 'c', (byte) 0xFF}))
                .satisfies(e -> assertThat(e.getSuppressed()).isEmpty());
        assertThat(tracked.out).hasToString("abc");
        assertThat(tracked.closed).isEqualTo(1);
    }

    @Test
    void failsOnBytesThatEndInTheMiddleOfACharacter() {
        var tracked = new TrackedSink();
        var sink = tracked.asByteSink(StandardCharsets.UTF_8);
        var bytes = "é".getBytes(StandardCharsets.UTF_8);

        assertThatExceptionOfType(MalformedInputException.class).isThrownBy(() -> sink.write(new byte[] {bytes[0]}));
        assertThat(tracked.closed).isEqualTo(1);
    }

    @Test
    void rejectsACharsetThatCannotEncode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CharSink.of(directory.resolve("text"), Charset.forName("ISO-2022-CN")))
                .withMessage("ISO-2022-CN does not support encoding");
    }

    // Keeps what is written to it, and counts the streams it closes.
    private static final class TrackedSink implements CharSink {

        private final StringWriter out = new StringWriter();
        private int closed;

        @Override
        public Writer openStream() {
            return new FilterWriter(out) {
                @Override
                public void close() throws IOException {
                    closed++;
                    super.close();
                }
            };
        }
    }
}
