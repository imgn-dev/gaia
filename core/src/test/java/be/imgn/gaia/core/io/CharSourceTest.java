package be.imgn.gaia.core.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import be.imgn.gaia.core.base.Charsets;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CharSourceTest {

    private static final String LINES = "first\nsecond\r\nthird\rfourth";

    @TempDir
    Path directory;

    @Test
    void readsAWrappedSequence() throws IOException {
        var source = CharSource.wrap("text");

        assertThat(source.read()).isEqualTo("text");
        assertThat(source.lengthIfKnown()).hasValue(4);
        assertThat(source.isEmpty()).isFalse();
    }

    @Test
    void readsTheCurrentContentOfAWrappedSequence() throws IOException {
        var text = new StringBuilder("one");
        var source = CharSource.wrap(text);

        text.append(" two");

        assertThat(source.read()).isEqualTo("one two");
        try (var in = source.openStream()) {
            assertThat(in.transferTo(new StringWriter())).isEqualTo(7);
        }
    }

    @Test
    void readsNothingFromTheEmptySource() throws IOException {
        var source = CharSource.empty();

        assertThat(source.read()).isEmpty();
        assertThat(source.length()).isZero();
        assertThat(source.isEmpty()).isTrue();
        assertThat(source.readFirstLine()).isEmpty();
        assertThat(source.readLines()).isEmpty();
    }

    @Test
    void readsALambda() throws IOException {
        CharSource source = () -> new StringReader("text");

        assertThat(source.read()).isEqualTo("text");
        assertThat(source.lengthIfKnown()).isEmpty();
        assertThat(source.length()).isEqualTo(4);
        assertThat(source.isEmpty()).isFalse();
        assertThat(((CharSource) () -> new StringReader("")).isEmpty()).isTrue();
    }

    @Test
    void closesTheStreamsItOpens() throws IOException {
        var source = new TrackedSource(LINES);

        source.read();
        source.length();
        source.isEmpty();
        source.readFirstLine();
        source.readLines();
        source.forEachLine(line -> {});
        source.copyTo(new StringBuilder());
        try (var lines = source.lines()) {
            assertThat(lines.count()).isEqualTo(4);
        }

        assertThat(source.opened).isEqualTo(8);
        assertThat(source.closed).isEqualTo(8);
    }

    @Test
    void readsLinesEndedByAnyTerminator() throws IOException {
        var source = CharSource.wrap(LINES);

        assertThat(source.readFirstLine()).hasValue("first");
        assertThat(source.readLines()).containsExactly("first", "second", "third", "fourth");
        try (var lines = source.lines()) {
            assertThat(lines).containsExactly("first", "second", "third", "fourth");
        }
        var read = new ArrayList<String>();
        source.forEachLine(read::add);
        assertThat(read).containsExactly("first", "second", "third", "fourth");
    }

    @Test
    void readsLinesIntoAnUnmodifiableList() throws IOException {
        List<String> lines = CharSource.wrap(LINES).readLines();

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> lines.add("fifth"));
    }

    @Test
    void readsAFinalLineTerminatorAsNoLine() throws IOException {
        assertThat(CharSource.wrap("a\nb\n").readLines()).containsExactly("a", "b");
        assertThat(CharSource.wrap("\n").readLines()).containsExactly("");
    }

    @Test
    void opensABufferedStream() throws IOException {
        CharSource source = () -> new StringReader("text");

        try (var in = source.openBufferedStream()) {
            assertThat(in.readLine()).isEqualTo("text");
        }
    }

    @Test
    void opensTheStreamItselfIfItIsBuffered() throws IOException {
        var reader = new BufferedReader(new StringReader("text"));
        CharSource source = () -> reader;

        try (var in = source.openBufferedStream()) {
            assertThat(in).isSameAs(reader);
        }
    }

    @Test
    void copiesToAnAppendable() throws IOException {
        var out = new StringBuilder("> ");

        assertThat(CharSource.wrap("text").copyTo(out)).isEqualTo(4);
        assertThat(out).hasToString("> text");
    }

    @Test
    void copiesToASink() throws IOException {
        var out = new StringWriter();

        assertThat(CharSource.wrap("text").copyTo(() -> out)).isEqualTo(4);
        assertThat(out).hasToString("text");
    }

    @Test
    void readsATextFile() throws IOException {
        var file = Files.writeString(directory.resolve("text"), "café", StandardCharsets.ISO_8859_1);

        assertThat(CharSource.of(file, StandardCharsets.ISO_8859_1).read()).isEqualTo("café");
        assertThat(CharSource.of(file.toUri().toURL(), StandardCharsets.ISO_8859_1)
                        .read())
                .isEqualTo("café");
        assertThat(CharSource.of(file.toUri(), StandardCharsets.ISO_8859_1).read())
                .isEqualTo("café");
    }

    @Test
    void readsATextFileInTheDefaultCharset() throws IOException {
        var file = Files.writeString(directory.resolve("text"), "café ✓", Charsets.DEFAULT_CHARSET);

        assertThat(CharSource.of(file).read()).isEqualTo("café ✓");
        assertThat(CharSource.of(file.toUri().toURL()).read()).isEqualTo("café ✓");
        assertThat(CharSource.of(file.toUri()).read()).isEqualTo("café ✓");
    }

    @Test
    void rejectsAUriThatIsNotAUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CharSource.of(URI.create("relative/path"), StandardCharsets.UTF_8));
    }

    @Test
    void readsConcatenatedSources() throws IOException {
        var source = CharSource.concat(CharSource.wrap("one"), CharSource.empty(), CharSource.wrap(" two"));

        assertThat(source.read()).isEqualTo("one two");
        assertThat(source.lengthIfKnown()).hasValue(7);
        assertThat(source.isEmpty()).isFalse();
    }

    @Test
    void opensEachConcatenatedSourceWhenItsTurnComes() throws IOException {
        var first = new TrackedSource("1");
        var second = new TrackedSource("2");

        try (var in = CharSource.concat(List.of(first, second)).openStream()) {
            assertThat(second.opened).isZero();
            var buffer = new char[2];
            assertThat(in.read(buffer)).isEqualTo(1);
            assertThat(in.read(buffer)).isEqualTo(1);

            assertThat(first.closed).isEqualTo(1);
            assertThat(second.opened).isEqualTo(1);
        }
        assertThat(second.closed).isEqualTo(1);
    }

    @Test
    void doesNotKnowTheLengthOfConcatenatedSourcesIfOneIsUnknown() throws IOException {
        var source = CharSource.concat(CharSource.wrap("one"), () -> new StringReader(" two"));

        assertThat(source.lengthIfKnown()).isEmpty();
        assertThat(source.length()).isEqualTo(7);
    }

    @Test
    void readsNothingFromNoConcatenatedSources() throws IOException {
        assertThat(CharSource.concat().read()).isEmpty();
        assertThat(CharSource.concat().isEmpty()).isTrue();
    }

    @Test
    void encodesCharacters() throws IOException {
        var source = CharSource.wrap("café ✓ 😀").asByteSource(StandardCharsets.UTF_8);

        assertThat(source.read()).isEqualTo("café ✓ 😀".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void encodesCharactersInTheDefaultCharset() throws IOException {
        var source = CharSource.wrap("café ✓ 😀").asByteSource();

        assertThat(source.read()).isEqualTo("café ✓ 😀".getBytes(Charsets.DEFAULT_CHARSET));
    }

    @Test
    void encodesCharactersBeyondABuffer() throws IOException {
        var text = "aé😀".repeat(10_000);
        var source = CharSource.wrap(text).asByteSource(StandardCharsets.UTF_8);
        var expected = text.getBytes(StandardCharsets.UTF_8);

        assertThat(source.read()).isEqualTo(expected);
        var out = new ByteArrayOutputStream();
        try (var in = source.openStream()) {
            for (var b = in.read(); b != -1; b = in.read()) {
                out.write(b);
            }
        }
        assertThat(out.toByteArray()).isEqualTo(expected);
    }

    @Test
    void encodesNothingForNoCharacters() throws IOException {
        assertThat(CharSource.empty().asByteSource(StandardCharsets.UTF_8).read())
                .isEmpty();
    }

    @Test
    void failsOnACharacterTheCharsetCannotEncode() {
        var source = CharSource.wrap("a✓b").asByteSource(StandardCharsets.ISO_8859_1);

        assertThatExceptionOfType(UnmappableCharacterException.class).isThrownBy(source::read);
    }

    @Test
    void failsOnAMalformedSurrogatePair() {
        var source = CharSource.wrap("a\uD83D").asByteSource(StandardCharsets.UTF_8);

        assertThatExceptionOfType(MalformedInputException.class).isThrownBy(source::read);
    }

    @Test
    void readsOnlyTheBytesBeforeACharacterThatFailsToEncode() throws IOException {
        var source = CharSource.wrap("ab\uD800c").asByteSource(StandardCharsets.UTF_8);

        try (var in = source.openStream()) {
            assertThatExceptionOfType(MalformedInputException.class).isThrownBy(in::read);
            assertThat(in.read()).isEqualTo('a');
            assertThat(in.read()).isEqualTo('b');
            assertThatExceptionOfType(MalformedInputException.class).isThrownBy(in::read);
        }
    }

    @Test
    void closesTheReaderOfTheEncodedBytes() throws IOException {
        var source = new TrackedSource("text");

        source.asByteSource(StandardCharsets.UTF_8).read();

        assertThat(source.closed).isEqualTo(1);
    }

    @Test
    void rejectsACharsetThatCannotEncode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CharSource.wrap("text").asByteSource(Charset.forName("ISO-2022-CN")))
                .withMessage("ISO-2022-CN does not support encoding");
    }

    // Counts the streams it opens and closes.
    private static final class TrackedSource implements CharSource {

        private final String text;
        private int opened;
        private int closed;

        TrackedSource(String text) {
            this.text = text;
        }

        @Override
        public Reader openStream() {
            opened++;
            return new FilterReader(new StringReader(text)) {
                @Override
                public void close() throws IOException {
                    closed++;
                    super.close();
                }
            };
        }
    }
}
