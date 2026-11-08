package be.imgn.gaia.core.io;

import static be.imgn.gaia.core.io.TestStreams.eachChunking;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.io.TestStreams.ChunkedReader;
import be.imgn.gaia.core.io.TestStreams.FailingWriter;
import java.io.Flushable;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.CharBuffer;
import org.junit.jupiter.api.Test;

class CharWriterTest {

    private static final String LARGE = "x".repeat(20_000);

    @Test
    void writesThroughABufferToAWriter() throws IOException {
        var stream = new CountingWriter();
        var out = CharWriter.of(stream);

        out.write("ab").write('c').write(CharBuffer.wrap("_def_"), 1, 4).writeCodePoint('g');
        assertThat(stream.toString()).isEmpty();

        out.flush();
        assertThat(stream.toString()).isEqualTo("abcdefg");
        assertThat(stream.flushCount()).isOne();
    }

    @Test
    void writesStraightToAnAppendable() throws IOException {
        var builder = new StringBuilder();
        var out = CharWriter.of(builder);

        out.write("ab").write('c').write(CharBuffer.wrap("_def_"), 1, 4).writeCodePoint('g');

        assertThat(builder).hasToString("abcdefg");
    }

    @Test
    void writesLargeTextInOrder() throws IOException {
        var stream = new StringWriter();
        try (var out = CharWriter.of(stream)) {
            out.write('<')
                    .write(LARGE)
                    .write(CharBuffer.wrap(LARGE))
                    .write("ab".repeat(5_000))
                    .write('>');
        }
        assertThat(stream.toString()).isEqualTo("<" + LARGE + LARGE + "ab".repeat(5_000) + ">");
    }

    @Test
    void writesCodePointsAndLines() throws IOException {
        var builder = new StringBuilder();
        var out = CharWriter.of(builder);

        out.writeCodePoint(0x1F600).writeLine("a").newLine();

        assertThat(builder).hasToString("😀a" + System.lineSeparator() + System.lineSeparator());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> out.writeCodePoint(0x110000))
                .withMessage("Not a valid code point: 1114112");
    }

    @Test
    void appendsAsAppendableSpecifies() throws IOException {
        for (Appendable target : new Appendable[] {new StringWriter(), new StringBuilder()}) {
            var out = CharWriter.of(target);

            out.append("ab")
                    .append(null)
                    .append("xcdx", 1, 3)
                    .append(null, 1, 3)
                    .append('e');
            out.flush();

            assertThat(target.toString()).isEqualTo("abnullcdule");
            assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> out.append("a", 0, 2));
        }
    }

    @Test
    void transfersAStream() throws IOException {
        var text = "héllo 😀 " + LARGE;
        for (var target : new Appendable[] {new StringWriter(), new StringBuilder()}) {
            for (var in : eachChunking(chunk -> new ChunkedReader(text, chunk))) {
                var out = CharWriter.of(target);
                assertThat(out.transferFrom(in.value())).as(in.name()).isEqualTo(text.length());
                out.flush();
                assertThat(target.toString()).as(in.name()).endsWith(text);
                assertThat(in.value().closeCount()).as(in.name()).isZero();
            }
        }
    }

    @Test
    void flushesAndClosesItsStreamOnce() throws IOException {
        var stream = new CountingWriter();
        var out = CharWriter.of(stream);
        out.write("abc");

        out.close();
        out.close();

        assertThat(stream.toString()).isEqualTo("abc");
        assertThat(stream.closeCount()).isOne();
        assertThatIOException().isThrownBy(() -> out.write('a')).withMessage("Writer closed");
        assertThatIOException().isThrownBy(() -> out.write("a"));
        assertThatIOException().isThrownBy(out::flush);
        assertThatIOException().isThrownBy(() -> out.transferFrom(new ChunkedReader("a", () -> 1)));
        assertThatIOException().isThrownBy(() -> out.asWriter().write('a'));
    }

    @Test
    void failsAfterClosingAnAppendable() throws IOException {
        var out = CharWriter.of(new StringBuilder());

        out.close();

        assertThatIOException().isThrownBy(() -> out.write('a'));
        assertThatIOException().isThrownBy(() -> out.write("a"));
        assertThatIOException().isThrownBy(() -> out.asWriter().write(new char[1], 0, 1));
    }

    @Test
    void flushesAnAppendableThatIsOnlyFlushable() throws IOException {
        var target = new FlushableAppendable();
        var out = CharWriter.of(target);

        out.flush();
        assertThat(target.flushCount()).isOne();

        out.close();
        assertThat(target.flushCount()).isEqualTo(2);
    }

    @Test
    void closesItsStreamEvenIfFlushingFails() throws IOException {
        var stream = new FailingWriter();
        var out = CharWriter.of(stream);
        out.write('a');

        assertThatIOException()
                .isThrownBy(out::close)
                .withMessage("write")
                .satisfies(e -> assertThat(e.getSuppressed())
                        .singleElement()
                        .extracting(Throwable::getMessage)
                        .isEqualTo("close"));
        assertThat(stream.closeCount()).isOne();
    }

    @Test
    void unwrapsItsStreamView() {
        var out = CharWriter.of(new StringBuilder());

        assertThat(CharWriter.of(out.asWriter())).isSameAs(out);
        assertThat(CharWriter.of((Appendable) out.asWriter())).isSameAs(out);
    }

    @Test
    void sharesItsBufferWithItsStreamView() throws IOException {
        for (var target : new Appendable[] {new CountingWriter(), new StringBuilder()}) {
            var out = CharWriter.of(target);
            var view = out.asWriter();

            out.write('a');
            view.write('b');
            out.write("c");
            view.write("_de_", 1, 2);
            view.write(new char[] {'_', 'f', 'g'}, 1, 2);
            view.write(LARGE.toCharArray(), 0, LARGE.length());
            view.flush();

            assertThat(target.toString()).isEqualTo("abcdefg" + LARGE);
            view.close();
            assertThatIOException().isThrownBy(() -> out.write('a'));
        }
    }

    @Test
    void rejectsInvalidRanges() {
        var out = CharWriter.of(new StringBuilder());

        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> out.write("a", 1, 0));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> out.asWriter().write(new char[1], 1, 1));
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> out.asWriter().write("a", 1, 1));
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNulls() {
        var out = CharWriter.of(new StringBuilder());

        assertThatNullPointerException()
                .isThrownBy(() -> CharWriter.of((Writer) null))
                .withMessage("out");
        assertThatNullPointerException()
                .isThrownBy(() -> CharWriter.of((Appendable) null))
                .withMessage("out");
        assertThatNullPointerException()
                .isThrownBy(() -> out.write((CharSequence) null))
                .withMessage("chars");
        assertThatNullPointerException().isThrownBy(() -> out.write(null, 0, 0)).withMessage("chars");
        assertThatNullPointerException().isThrownBy(() -> out.writeLine(null)).withMessage("chars");
        assertThatNullPointerException()
                .isThrownBy(() -> out.transferFrom(null))
                .withMessage("in");
    }

    private static final class CountingWriter extends StringWriter {

        private int flushCount;
        private int closeCount;

        @Override
        public void flush() {
            flushCount++;
        }

        @Override
        public void close() {
            closeCount++;
        }

        int flushCount() {
            return flushCount;
        }

        int closeCount() {
            return closeCount;
        }
    }

    private static final class FlushableAppendable implements Appendable, Flushable {

        private final StringBuilder builder = new StringBuilder();
        private int flushCount;

        @Override
        public Appendable append(CharSequence csq) {
            builder.append(csq);
            return this;
        }

        @Override
        public Appendable append(CharSequence csq, int start, int end) {
            builder.append(csq, start, end);
            return this;
        }

        @Override
        public Appendable append(char c) {
            builder.append(c);
            return this;
        }

        @Override
        public void flush() {
            flushCount++;
        }

        int flushCount() {
            return flushCount;
        }
    }
}
