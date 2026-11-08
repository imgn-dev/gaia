package be.imgn.gaia.core.io;

import static be.imgn.gaia.core.io.TestStreams.eachChunking;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.io.TestStreams.ChunkedReader;
import be.imgn.gaia.core.io.TestStreams.Named;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class CharReaderTest {

    // More than three buffers, so that reads cross the end of the buffer.
    private static final int SIZE = 30_000;

    // A reader on the characters directly, and readers on streams that return them in chunks of every kind.
    private static List<Named<CharReader>> readers(String chars) {
        var readers = new ArrayList<Named<CharReader>>();
        readers.add(new Named<>("chars", CharReader.of(CharBuffer.wrap(chars))));
        for (var stream : eachChunking(chunk -> new ChunkedReader(chars, chunk))) {
            readers.add(new Named<>(stream.name(), CharReader.of(stream.value())));
        }
        return readers;
    }

    private static String randomText(long seed) {
        var random = new SplittableRandom(seed);
        var pieces = new String[] {"a", "b", "é", "日本", "😀", " ", "\t", "=", "\n", "\r", "\r\n"};
        var text = new StringBuilder();
        while (text.length() < SIZE) {
            text.append(pieces[random.nextInt(pieces.length)]);
        }
        return text.append("end").toString();
    }

    @Test
    void readsCharactersAndCodePoints() throws IOException {
        var text = randomText(1);
        for (var reader : readers(text)) {
            var in = reader.value();
            var name = reader.name();
            var operations = new SplittableRandom(2);
            var position = 0;
            while (position < text.length() - 1) {
                if (operations.nextBoolean()) {
                    assertThat(in.readChar()).as(name).isEqualTo(text.charAt(position));
                    position++;
                } else {
                    var codePoint = text.codePointAt(position);
                    assertThat(in.readCodePoint()).as(name).isEqualTo(codePoint);
                    position += Character.charCount(codePoint);
                }
            }
            assertThat(in.readAll()).as(name).isEqualTo(text.substring(position));
        }
    }

    @Test
    void readsAnUnpairedSurrogateAsItIs() throws IOException {
        var in = CharReader.of("\uD83Dx\uDE00\uD83D");

        assertThat(in.readCodePoint()).isEqualTo(0xD83D);
        assertThat(in.readCodePoint()).isEqualTo('x');
        assertThat(in.readCodePoint()).isEqualTo(0xDE00);
        assertThat(in.readCodePoint()).isEqualTo(0xD83D);
        assertThatExceptionOfType(EOFException.class).isThrownBy(in::readCodePoint);
    }

    @Test
    void failsAtTheEndOfTheStreamAndStaysThere() throws IOException {
        List<CharReaderCall> calls =
                List.of(in -> in.readString(4), in -> in.skip(4), in -> in.readChar(), in -> in.readString(20_000));
        for (var call : calls) {
            for (var reader : readers("abc")) {
                var in = reader.value();
                in.readChar();
                in.readChar();
                in.readChar();
                assertThatExceptionOfType(EOFException.class).as(reader.name()).isThrownBy(() -> call.accept(in));
                assertThat(in.exhausted()).as(reader.name()).isTrue();
            }
        }
        for (var reader : readers("abc")) {
            assertThatExceptionOfType(EOFException.class)
                    .as(reader.name())
                    .isThrownBy(() -> reader.value().readString(4));
            assertThat(reader.value().exhausted()).as(reader.name()).isTrue();
        }
    }

    @Test
    void looksAheadWithoutConsuming() throws IOException {
        var text = randomText(3);
        for (var reader : readers(text)) {
            var in = reader.value();
            var name = reader.name();

            assertThat(in.request(20_000)).as(name).isTrue();
            assertThat(in.peek()).as(name).isEqualTo(text.charAt(0));
            assertThat(in.startsWith(text.substring(0, 10_000))).as(name).isTrue();
            assertThat(in.startsWith(text.substring(1, 10_000))).as(name).isFalse();
            assertThat(in.startsWith("")).as(name).isTrue();
            in.require(text.length());
            assertThat(in.request(text.length() + 1)).as(name).isFalse();
            assertThatExceptionOfType(EOFException.class).as(name).isThrownBy(() -> in.require(text.length() + 1));
            assertThat(in.exhausted()).as(name).isFalse();

            assertThat(in.readString(text.length())).as(name).isEqualTo(text);
            assertThat(in.exhausted()).as(name).isTrue();
            assertThat(in.peek()).as(name).isEqualTo(-1);
        }
    }

    @Test
    void findsCharactersAhead() throws IOException {
        var text = "a".repeat(12_000) + "xyz" + "a".repeat(2_997) + "!" + "a".repeat(5_000);
        for (var reader : readers(text)) {
            var in = reader.value();
            var name = reader.name();

            assertThat(in.indexOf('!', 15_000)).as(name).isEqualTo(-1);
            assertThat(in.indexOf('!', 15_001)).as(name).isEqualTo(15_000);
            assertThat(in.indexOf('?', 50_000)).as(name).isEqualTo(-1);
            assertThat(in.indexOf("xyz", 12_002)).as(name).isEqualTo(-1);
            assertThat(in.indexOf("xyz", 12_003)).as(name).isEqualTo(12_000);
            assertThat(in.indexOf("xyw", 50_000)).as(name).isEqualTo(-1);
            assertThat(in.indexOf("", 0)).as(name).isZero();

            assertThat(in.readAll()).as(name).isEqualTo(text);
        }
    }

    @Test
    void readsLinesAsBufferedReaderDoes() throws IOException {
        var text = randomText(4);
        var expected = new ArrayList<String>();
        var lines = new BufferedReader(new StringReader(text));
        for (var line = lines.readLine(); line != null; line = lines.readLine()) {
            expected.add(line);
        }
        for (var reader : readers(text)) {
            var actual = new ArrayList<String>();
            for (var line = reader.value().readLine(1_000);
                    line.isPresent();
                    line = reader.value().readLine(1_000)) {
                actual.add(line.get());
            }
            assertThat(actual).as(reader.name()).isEqualTo(expected);
        }
    }

    @Test
    void limitsTheLengthOfALine() throws IOException {
        for (var reader : readers("abcdef\nabcdefg\r\n\n")) {
            var in = reader.value();
            var name = reader.name();

            assertThat(in.readLine(6)).as(name).hasValue("abcdef");
            assertThatIOException()
                    .as(name)
                    .isThrownBy(() -> in.readLine(6))
                    .withMessage("Line longer than 6 characters");
            assertThat(in.readLine(7)).as(name).hasValue("abcdefg");
            assertThat(in.readLine(0)).as(name).hasValue("");
            assertThat(in.readLine(0)).as(name).isEmpty();
        }
    }

    @Test
    void readsAndSkipsRunsOfCodePoints() throws IOException {
        for (var reader : readers("  \t key😀1 = value 😀😀😀x")) {
            var in = reader.value();
            var name = reader.name();

            assertThat(in.skipWhile(Character::isWhitespace)).as(name).isEqualTo(4);
            assertThat(in.readWhile(c -> c != '=' && !Character.isWhitespace(c), 100))
                    .as(name)
                    .isEqualTo("key😀1");
            assertThat(in.skipWhile(c -> c == ' ')).as(name).isOne();
            assertThat(in.readWhile(c -> c == ' ', 100)).as(name).isEmpty();
            assertThat(in.readChar()).as(name).isEqualTo('=');
            in.skipWhile(Character::isWhitespace);
            assertThat(in.readWhile(Character::isLetter, 3)).as(name).isEqualTo("val");
            assertThat(in.readWhile(Character::isLetter, 3)).as(name).isEqualTo("ue");
            in.skip(1);
            // A surrogate pair that would exceed the limit is left.
            assertThat(in.readWhile(c -> c == 0x1F600, 5)).as(name).isEqualTo("😀😀");
            assertThat(in.readWhile(c -> c == 0x1F600, 5)).as(name).isEqualTo("😀");
            assertThat(in.skipWhile(c -> true)).as(name).isOne();
            assertThat(in.skipWhile(c -> true)).as(name).isZero();
            assertThat(in.readWhile(c -> true, 5)).as(name).isEmpty();
        }
    }

    @Test
    void readsAndSkipsLongRuns() throws IOException {
        var text = randomText(5);
        for (var reader : readers(text)) {
            var in = reader.value();
            var name = reader.name();

            assertThat(in.readString(20_000)).as(name).isEqualTo(text.substring(0, 20_000));
            in.skip(5_000);
            assertThat(in.readAll()).as(name).isEqualTo(text.substring(25_000));
        }
    }

    @Test
    void transfersTheRest() throws IOException {
        var text = randomText(6);
        for (var reader : readers(text)) {
            var writer = new StringWriter();
            reader.value().skip(3);
            assertThat(reader.value().transferTo(writer)).as(reader.name()).isEqualTo(text.length() - 3);
            assertThat(writer.toString()).as(reader.name()).isEqualTo(text.substring(3));
        }
        for (var reader : readers(text)) {
            var builder = new StringBuilder();
            assertThat(reader.value().transferTo(builder)).as(reader.name()).isEqualTo(text.length());
            assertThat(builder.toString()).as(reader.name()).isEqualTo(text);
        }
    }

    @Test
    void unwrapsItsStreamView() {
        var in = CharReader.of("a");

        assertThat(CharReader.of(in.asReader())).isSameAs(in);
    }

    @Test
    void sharesItsPositionWithItsStreamView() throws IOException {
        var text = randomText(7);
        for (var reader : readers(text)) {
            var in = reader.value();
            var name = reader.name();
            var view = in.asReader();

            assertThat(in.readChar()).as(name).isEqualTo(text.charAt(0));
            assertThat(view.read()).as(name).isEqualTo(text.charAt(1));
            assertThat(in.readString(2)).as(name).isEqualTo(text.substring(2, 4));
            // Ready once a character is buffered, whatever the stream says.
            in.request(1);
            assertThat(view.ready()).as(name).isTrue();
            var chars = new char[10_000];
            var read = view.read(chars, 0, chars.length);
            assertThat(new String(chars, 0, read)).as(name).isEqualTo(text.substring(4, 4 + read));
            var skipped = view.skip(6);
            assertThat(skipped).as(name).isPositive().isLessThanOrEqualTo(6);
            var writer = new StringWriter();
            view.transferTo(writer);
            assertThat(writer.toString()).as(name).isEqualTo(text.substring(4 + read + (int) skipped));
            assertThat(view.read()).as(name).isEqualTo(-1);
            assertThat(view.read(chars, 0, 0)).as(name).isZero();
        }
    }

    @Test
    void closesItsStreamOnce() throws IOException {
        var stream = new ChunkedReader("ab", () -> 1);
        var in = CharReader.of(stream);

        in.close();
        in.close();

        assertThat(stream.closeCount()).isOne();
        assertThatIOException().isThrownBy(in::readChar).withMessage("Reader closed");
        assertThatIOException().isThrownBy(in::peek);
        assertThatIOException().isThrownBy(in::readAll);
        assertThatIOException().isThrownBy(() -> in.skip(1));
        assertThatIOException().isThrownBy(() -> in.transferTo(new StringBuilder()));
        assertThatIOException().isThrownBy(() -> in.asReader().read());
        assertThatIOException().isThrownBy(() -> in.asReader().ready());
    }

    @Test
    void closesThroughItsStreamView() throws IOException {
        var stream = new ChunkedReader("ab", () -> 1);
        var in = CharReader.of(stream);

        in.asReader().close();

        assertThat(stream.closeCount()).isOne();
        assertThatIOException().isThrownBy(in::readChar);
    }

    @Test
    void rejectsNegativeCounts() {
        var in = CharReader.of("a");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> in.request(-1))
                .withMessage("charCount must not be negative: -1");
        assertThatIllegalArgumentException().isThrownBy(() -> in.require(-1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> in.indexOf('a', -1))
                .withMessage("maxChars must not be negative: -1");
        assertThatIllegalArgumentException().isThrownBy(() -> in.indexOf("a", -1));
        assertThatIllegalArgumentException().isThrownBy(() -> in.readString(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> in.readLine(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> in.readWhile(c -> true, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> in.skip(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> in.asReader().skip(-1));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> in.asReader().read(new char[1], 1, 1));
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNulls() {
        var in = CharReader.of("a");

        assertThatNullPointerException()
                .isThrownBy(() -> CharReader.of((Reader) null))
                .withMessage("in");
        assertThatNullPointerException()
                .isThrownBy(() -> CharReader.of((CharSequence) null))
                .withMessage("chars");
        assertThatNullPointerException().isThrownBy(() -> in.startsWith(null)).withMessage("prefix");
        assertThatNullPointerException().isThrownBy(() -> in.indexOf(null, 1)).withMessage("target");
        assertThatNullPointerException().isThrownBy(() -> in.readWhile(null, 1)).withMessage("test");
        assertThatNullPointerException().isThrownBy(() -> in.skipWhile(null)).withMessage("test");
        assertThatNullPointerException().isThrownBy(() -> in.transferTo(null)).withMessage("out");
    }

    @FunctionalInterface
    private interface CharReaderCall {
        void accept(CharReader in) throws IOException;
    }
}
