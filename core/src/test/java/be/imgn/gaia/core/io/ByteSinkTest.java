package be.imgn.gaia.core.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import be.imgn.gaia.core.base.Charsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ByteSinkTest {

    @TempDir
    Path directory;

    @Test
    void writesBytes() throws IOException {
        var sink = new TrackedSink();

        sink.write(new byte[] {1, 2, 3});

        assertThat(sink.out.toByteArray()).containsExactly(1, 2, 3);
        assertThat(sink.closed).isEqualTo(1);
    }

    @Test
    void writesASliceOfBytes() throws IOException {
        var sink = new TrackedSink();

        sink.write(Bytes.of(new byte[] {1, 2, 3, 4}).slice(1, 3));

        assertThat(sink.out.toByteArray()).containsExactly(2, 3);
        assertThat(sink.closed).isEqualTo(1);
    }

    @Test
    void writesTheBytesOfAStreamAndLeavesItOpen() throws IOException {
        var sink = new TrackedSink();
        var in = new ByteArrayInputStream(new byte[] {1, 2, 3, 4}, 1, 3);

        assertThat(sink.writeFrom(in)).isEqualTo(3);
        assertThat(sink.out.toByteArray()).containsExactly(2, 3, 4);
        assertThat(sink.closed).isEqualTo(1);
    }

    @Test
    void createsAFile() throws IOException {
        var file = directory.resolve("data");

        ByteSink.of(file).write(new byte[] {1, 2});

        assertThat(file).hasBinaryContent(new byte[] {1, 2});
    }

    @Test
    void replacesTheContentOfAFile() throws IOException {
        var file = Files.write(directory.resolve("data"), new byte[] {1, 2, 3, 4});

        ByteSink.of(file).write(new byte[] {5});

        assertThat(file).hasBinaryContent(new byte[] {5});
    }

    @Test
    void appendsToAFile() throws IOException {
        var file = Files.write(directory.resolve("data"), new byte[] {1, 2});

        ByteSink.of(file, StandardOpenOption.APPEND).write(new byte[] {3});

        assertThat(file).hasBinaryContent(new byte[] {1, 2, 3});
    }

    @Test
    void encodesCharacters() throws IOException {
        var sink = new TrackedSink();

        sink.asCharSink(StandardCharsets.UTF_8).write("café ✓");

        assertThat(sink.out.toByteArray()).isEqualTo("café ✓".getBytes(StandardCharsets.UTF_8));
        assertThat(sink.closed).isEqualTo(1);
    }

    @Test
    void encodesCharactersInTheDefaultCharset() throws IOException {
        var sink = new TrackedSink();

        sink.asCharSink().write("café ✓");

        assertThat(sink.out.toByteArray()).isEqualTo("café ✓".getBytes(Charsets.DEFAULT_CHARSET));
        assertThat(sink.closed).isEqualTo(1);
    }

    @Test
    void encodesCharactersBeyondABuffer() throws IOException {
        var sink = new TrackedSink();
        var text = "aé😀".repeat(10_000);

        sink.asCharSink(StandardCharsets.UTF_8).write(text);

        assertThat(sink.out.toByteArray()).isEqualTo(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void encodesASurrogatePairSplitAcrossWrites() throws IOException {
        var out = new ByteArrayOutputStream();

        try (var writer = new EncodingWriter(ByteWriter.of(out), StandardCharsets.UTF_8.newEncoder())) {
            writer.write("a\uD83D");
            writer.write("\uDE00b");
        }

        assertThat(out.toByteArray()).isEqualTo("a😀b".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void failsOnAHighSurrogateAtTheEnd() {
        var tracked = new TrackedSink();
        var sink = tracked.asCharSink(StandardCharsets.UTF_8);

        assertThatExceptionOfType(MalformedInputException.class).isThrownBy(() -> sink.write("a\uD83D"));
        assertThat(tracked.closed).isEqualTo(1);
    }

    @Test
    void failsOnACharacterTheCharsetCannotEncode() {
        var sink = new TrackedSink().asCharSink(StandardCharsets.ISO_8859_1);

        assertThatExceptionOfType(UnmappableCharacterException.class).isThrownBy(() -> sink.write("a✓b"));
    }

    @Test
    void rejectsACharsetThatCannotEncode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TrackedSink().asCharSink(Charset.forName("ISO-2022-CN")))
                .withMessage("ISO-2022-CN does not support encoding");
    }

    @Test
    void opensAWriter() throws IOException {
        var file = directory.resolve("data");

        try (var out = ByteSink.of(file).openWriter()) {
            out.writeShort(0x0102).writeByte(3);
        }

        assertThat(Files.readAllBytes(file)).containsExactly(1, 2, 3);
    }

    // Keeps what is written to it, and counts the streams it closes.
    private static final class TrackedSink implements ByteSink {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private int closed;

        @Override
        public ByteWriter openWriter() {
            return ByteWriter.of(new FilterOutputStream(out) {
                @Override
                public void close() throws IOException {
                    closed++;
                    super.close();
                }
            });
        }
    }
}
