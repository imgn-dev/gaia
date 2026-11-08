package be.imgn.gaia.core.io;

import static be.imgn.gaia.core.io.TestStreams.eachChunking;
import static be.imgn.gaia.core.io.TestStreams.randomBytes;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.io.TestStreams.ChunkedInputStream;
import be.imgn.gaia.core.io.TestStreams.Named;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ByteReaderTest {

    // More than three buffers, so that reads cross the end of the buffer.
    private static final int SIZE = 30_000;

    // A reader on the bytes directly, and readers on streams that return them in chunks of every kind.
    private static List<Named<ByteReader>> readers(byte[] bytes) {
        var readers = new ArrayList<Named<ByteReader>>();
        readers.add(new Named<>("bytes", ByteReader.of(Bytes.of(bytes))));
        for (var stream : eachChunking(chunk -> new ChunkedInputStream(bytes, chunk))) {
            readers.add(new Named<>(stream.name(), ByteReader.of(stream.value())));
        }
        return readers;
    }

    @Test
    void readsBigEndianNumbersAsDataInputStreamDoes() throws IOException {
        var bytes = randomBytes(1, SIZE);
        for (var reader : readers(bytes)) {
            var in = reader.value();
            var name = reader.name();
            var expected = new DataInputStream(new ByteArrayInputStream(bytes));
            var operations = new SplittableRandom(2);
            while (expected.available() >= Long.BYTES) {
                switch (operations.nextInt(9)) {
                    case 0 -> assertThat(in.readByte()).as(name).isEqualTo(expected.readByte());
                    case 1 -> assertThat(in.readUnsignedByte()).as(name).isEqualTo(expected.readUnsignedByte());
                    case 2 -> assertThat(in.readShort()).as(name).isEqualTo(expected.readShort());
                    case 3 -> assertThat(in.readUnsignedShort()).as(name).isEqualTo(expected.readUnsignedShort());
                    case 4 -> assertThat(in.readInt()).as(name).isEqualTo(expected.readInt());
                    case 5 ->
                        assertThat(in.readUnsignedInt()).as(name).isEqualTo(Integer.toUnsignedLong(expected.readInt()));
                    case 6 -> assertThat(in.readLong()).as(name).isEqualTo(expected.readLong());
                    case 7 ->
                        assertThat(Float.floatToRawIntBits(in.readFloat()))
                                .as(name)
                                .isEqualTo(Float.floatToRawIntBits(expected.readFloat()));
                    default ->
                        assertThat(Double.doubleToRawLongBits(in.readDouble()))
                                .as(name)
                                .isEqualTo(Double.doubleToRawLongBits(expected.readDouble()));
                }
            }
            assertThat(in.readAll()).as(name).isEqualTo(Bytes.of(expected.readAllBytes()));
        }
    }

    @Test
    void readsLittleEndianNumbers() throws IOException {
        var bytes = randomBytes(3, SIZE);
        for (var reader : readers(bytes)) {
            var in = reader.value();
            var name = reader.name();
            var expected = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            var operations = new SplittableRandom(4);
            while (expected.remaining() >= Long.BYTES) {
                switch (operations.nextInt(8)) {
                    case 0 -> assertThat(in.readByte()).as(name).isEqualTo(expected.get());
                    case 1 -> assertThat(in.readShortLe()).as(name).isEqualTo(expected.getShort());
                    case 2 ->
                        assertThat(in.readUnsignedShortLe())
                                .as(name)
                                .isEqualTo(Short.toUnsignedInt(expected.getShort()));
                    case 3 -> assertThat(in.readIntLe()).as(name).isEqualTo(expected.getInt());
                    case 4 ->
                        assertThat(in.readUnsignedIntLe())
                                .as(name)
                                .isEqualTo(Integer.toUnsignedLong(expected.getInt()));
                    case 5 -> assertThat(in.readLongLe()).as(name).isEqualTo(expected.getLong());
                    case 6 ->
                        assertThat(Float.floatToRawIntBits(in.readFloatLe()))
                                .as(name)
                                .isEqualTo(Float.floatToRawIntBits(expected.getFloat()));
                    default ->
                        assertThat(Double.doubleToRawLongBits(in.readDoubleLe()))
                                .as(name)
                                .isEqualTo(Double.doubleToRawLongBits(expected.getDouble()));
                }
            }
        }
    }

    @Test
    void failsAtTheEndOfTheStreamAndStaysThere() throws IOException {
        var bytes = new byte[] {1, 2, 3};
        for (var reader : readers(bytes)) {
            var in = reader.value();
            assertThatExceptionOfType(EOFException.class).as(reader.name()).isThrownBy(in::readInt);
            assertThat(in.exhausted()).as(reader.name()).isTrue();
        }
        List<ByteReaderCall> calls = List.of(
                in -> in.readBytes(4),
                in -> in.readFully(new byte[4], 0, 4),
                in -> in.skip(4),
                in -> in.readString(4),
                in -> in.readLongLe());
        for (var call : calls) {
            for (var reader : readers(bytes)) {
                var in = reader.value();
                assertThatExceptionOfType(EOFException.class).as(reader.name()).isThrownBy(() -> call.accept(in));
                assertThat(in.exhausted()).as(reader.name()).isTrue();
            }
        }
        for (var reader : readers(randomBytes(5, SIZE))) {
            assertThatExceptionOfType(EOFException.class)
                    .as(reader.name())
                    .isThrownBy(() -> reader.value().readBytes(SIZE + 1));
            assertThatExceptionOfType(EOFException.class)
                    .as(reader.name())
                    .isThrownBy(() -> reader.value().readByte());
        }
    }

    @Test
    void failsToSkipPastTheEndOfAFile(@TempDir Path directory) throws IOException {
        var file = Files.write(directory.resolve("data"), new byte[] {1, 2, 3});

        try (var in = ByteSource.of(file).openReader()) {
            assertThatExceptionOfType(EOFException.class).isThrownBy(() -> in.skip(4));
            assertThat(in.exhausted()).isTrue();
        }
    }

    @Test
    void looksAheadWithoutConsuming() throws IOException {
        var bytes = randomBytes(6, SIZE);
        for (var reader : readers(bytes)) {
            var in = reader.value();
            var name = reader.name();

            assertThat(in.request(20_000)).as(name).isTrue();
            assertThat(in.peek()).as(name).isEqualTo(Byte.toUnsignedInt(bytes[0]));
            assertThat(in.startsWith(Bytes.copyOf(bytes, 0, 10_000))).as(name).isTrue();
            assertThat(in.startsWith(Bytes.copyOf(bytes, 1, 10_000))).as(name).isFalse();
            assertThat(in.startsWith(Bytes.empty())).as(name).isTrue();
            in.require(SIZE);
            assertThat(in.request(SIZE + 1)).as(name).isFalse();
            assertThatExceptionOfType(EOFException.class).as(name).isThrownBy(() -> in.require(SIZE + 1));
            assertThat(in.startsWith(Bytes.of(new byte[SIZE + 1]))).as(name).isFalse();
            assertThat(in.exhausted()).as(name).isFalse();

            assertThat(in.readBytes(SIZE)).as(name).isEqualTo(Bytes.of(bytes));
            assertThat(in.exhausted()).as(name).isTrue();
            assertThat(in.peek()).as(name).isEqualTo(-1);
            assertThat(in.request(0)).as(name).isTrue();
        }
    }

    @Test
    void findsBytesAhead() throws IOException {
        var bytes = new byte[20_000];
        bytes[15_000] = 7;
        bytes[12_000] = 1;
        bytes[12_001] = 2;
        bytes[12_002] = 3;
        var target = Bytes.of(new byte[] {1, 2, 3});
        for (var reader : readers(bytes)) {
            var in = reader.value();
            var name = reader.name();

            assertThat(in.indexOf((byte) 7, 15_000)).as(name).isEqualTo(-1);
            assertThat(in.indexOf((byte) 7, 15_001)).as(name).isEqualTo(15_000);
            assertThat(in.indexOf((byte) 9, 50_000)).as(name).isEqualTo(-1);
            assertThat(in.indexOf((byte) 0, 0)).as(name).isEqualTo(-1);
            assertThat(in.indexOf(target, 12_002)).as(name).isEqualTo(-1);
            assertThat(in.indexOf(target, 12_003)).as(name).isEqualTo(12_000);
            assertThat(in.indexOf(Bytes.of(new byte[] {1, 2, 4}), 50_000))
                    .as(name)
                    .isEqualTo(-1);
            assertThat(in.indexOf(Bytes.empty(), 0)).as(name).isZero();

            assertThat(in.readBytes(20_000)).as(name).isEqualTo(Bytes.of(bytes));
        }
    }

    @Test
    void readsRunsOfBytes() throws IOException {
        var bytes = randomBytes(7, SIZE);
        for (var reader : readers(bytes)) {
            var in = reader.value();
            var name = reader.name();

            assertThat(in.readBytes(10)).as(name).isEqualTo(Bytes.copyOf(bytes, 0, 10));
            assertThat(in.readBytes(0)).as(name).isEqualTo(Bytes.empty());
            // Larger than the buffer.
            assertThat(in.readBytes(9_000)).as(name).isEqualTo(Bytes.copyOf(bytes, 10, 9_000));
            var array = new byte[10_002];
            in.readFully(array, 1, 10_000);
            assertThat(Arrays.copyOfRange(array, 1, 10_001))
                    .as(name)
                    .isEqualTo(Arrays.copyOfRange(bytes, 9_010, 19_010));
            in.readFully(array, 0, 10);
            assertThat(Arrays.copyOf(array, 10)).as(name).isEqualTo(Arrays.copyOfRange(bytes, 19_010, 19_020));
            in.skip(5);
            in.skip(0);
            var small = new byte[100];
            assertThat(in.read(small, 0, 0)).as(name).isZero();
            var read = in.read(small, 0, 100);
            assertThat(read).as(name).isBetween(1, 100);
            assertThat(Arrays.copyOf(small, read)).as(name).isEqualTo(Arrays.copyOfRange(bytes, 19_025, 19_025 + read));
            var position = 19_025 + read;
            // Past the buffer.
            in.skip(9_000);
            position += 9_000;
            assertThat(in.readAll()).as(name).isEqualTo(Bytes.copyOf(bytes, position, SIZE - position));
            assertThat(in.read(small, 0, 100)).as(name).isEqualTo(-1);
            assertThat(in.readAll()).as(name).isEqualTo(Bytes.empty());
        }
    }

    @Test
    void readsLargeArraysStraightFromTheStream() throws IOException {
        var bytes = randomBytes(8, SIZE);
        for (var reader : readers(bytes)) {
            var in = reader.value();
            var array = new byte[SIZE];
            var total = 0;
            while (total < SIZE) {
                total += in.read(array, total, SIZE - total);
            }
            assertThat(array).as(reader.name()).isEqualTo(bytes);
        }
    }

    @Test
    void transfersTheRestToAStream() throws IOException {
        var bytes = randomBytes(9, SIZE);
        for (var reader : readers(bytes)) {
            var in = reader.value();
            in.readInt();
            var out = new ByteArrayOutputStream();

            assertThat(in.transferTo(out)).as(reader.name()).isEqualTo(SIZE - 4);
            assertThat(out.toByteArray()).as(reader.name()).isEqualTo(Arrays.copyOfRange(bytes, 4, SIZE));
            assertThat(in.exhausted()).as(reader.name()).isTrue();
        }
    }

    @Test
    void readsLinesAsBufferedReaderDoes() throws IOException {
        var random = new SplittableRandom(10);
        var pieces = new String[] {"a", "b", "é", "日本", "😀", " ", "\n", "\r", "\r\n"};
        var text = new StringBuilder();
        while (text.length() < SIZE) {
            text.append(pieces[random.nextInt(pieces.length)]);
        }
        text.append("end");
        var expected = new ArrayList<String>();
        var lines = new BufferedReader(new StringReader(text.toString()));
        for (var line = lines.readLine(); line != null; line = lines.readLine()) {
            expected.add(line);
        }
        for (var reader : readers(text.toString().getBytes(UTF_8))) {
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
        var bytes = "abcdef\nabcdefg\r\n\n".getBytes(UTF_8);
        for (var reader : readers(bytes)) {
            var in = reader.value();
            var name = reader.name();

            assertThat(in.readLine(6)).as(name).hasValue("abcdef");
            assertThatIOException().as(name).isThrownBy(() -> in.readLine(6)).withMessage("Line longer than 6 bytes");
            assertThat(in.readLine(7)).as(name).hasValue("abcdefg");
            assertThat(in.readLine(0)).as(name).hasValue("");
            assertThat(in.readLine(0)).as(name).isEmpty();
        }
    }

    @Test
    void readsLinesInCharsetsThatEncodeTerminatorsAsAscii() throws IOException {
        var in = ByteReader.of(Bytes.encode("é\r\nü", ISO_8859_1));

        assertThat(in.readLine(10, ISO_8859_1)).hasValue("é");
        assertThat(in.readLine(10, ISO_8859_1)).hasValue("ü");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> in.readLine(10, UTF_16))
                .withMessage("UTF-16 doesn't encode line terminators as ASCII does");
    }

    @Test
    void readsText() throws IOException {
        var large = "x".repeat(20_000);
        for (var reader : readers(("héllo" + large).getBytes(UTF_8))) {
            var in = reader.value();

            assertThat(in.readString(6)).as(reader.name()).isEqualTo("héllo");
            assertThat(in.readString(20_000)).as(reader.name()).isEqualTo(large);
        }
        var in = ByteReader.of(Bytes.encode("héllo", ISO_8859_1));
        assertThat(in.readString(5, ISO_8859_1)).isEqualTo("héllo");
    }

    @Test
    void replacesMalformedText() throws IOException {
        var in = ByteReader.of(Bytes.of(new byte[] {'a', (byte) 0xFF, 'b', '\n', (byte) 0xC3}));

        assertThat(in.readLine(10)).hasValue("a�b");
        assertThat(in.readString(1)).isEqualTo("�");
    }

    @Test
    void unwrapsItsStreamView() {
        var in = ByteReader.of(Bytes.of(new byte[] {1}));

        assertThat(ByteReader.of(in.asInputStream())).isSameAs(in);
    }

    @Test
    void sharesItsPositionWithItsStreamView() throws IOException {
        var bytes = randomBytes(11, SIZE);
        for (var reader : readers(bytes)) {
            var in = reader.value();
            var name = reader.name();
            var view = in.asInputStream();

            assertThat(in.readByte()).as(name).isEqualTo(bytes[0]);
            assertThat(view.read()).as(name).isEqualTo(Byte.toUnsignedInt(bytes[1]));
            assertThat(in.readBytes(2)).as(name).isEqualTo(Bytes.copyOf(bytes, 2, 2));
            assertThat(view.readNBytes(10_000)).as(name).isEqualTo(Arrays.copyOfRange(bytes, 4, 10_004));
            assertThat(view.skip(6)).as(name).isPositive().isLessThanOrEqualTo(6);
        }
    }

    @Test
    void handsTheRestOfTheStreamToOtherApis() throws IOException {
        var data = randomBytes(12, SIZE);
        var compressed = new ByteArrayOutputStream();
        try (var out = ByteWriter.of(compressed)) {
            out.writeInt(0x1F8B);
            var gzip = new GZIPOutputStream(out.asOutputStream());
            gzip.write(data);
            gzip.finish();
        }
        for (var reader : readers(compressed.toByteArray())) {
            var in = reader.value();

            assertThat(in.readInt()).as(reader.name()).isEqualTo(0x1F8B);
            assertThat(new GZIPInputStream(in.asInputStream()).readAllBytes())
                    .as(reader.name())
                    .isEqualTo(data);
        }
    }

    @Test
    void closesItsStreamOnce() throws IOException {
        var stream = new ChunkedInputStream(new byte[] {1, 2}, () -> 1);
        var in = ByteReader.of(stream);

        in.close();
        in.close();

        assertThat(stream.closeCount()).isOne();
        assertThatIOException().isThrownBy(in::readByte).withMessage("Reader closed");
        assertThatIOException().isThrownBy(in::peek);
        assertThatIOException().isThrownBy(in::readAll);
        assertThatIOException().isThrownBy(() -> in.skip(1));
        assertThatIOException().isThrownBy(() -> in.readFully(new byte[1], 0, 1));
        assertThatIOException().isThrownBy(() -> in.read(new byte[1], 0, 1));
        assertThatIOException().isThrownBy(() -> in.transferTo(new ByteArrayOutputStream()));
        assertThatIOException().isThrownBy(() -> in.asInputStream().read());
        assertThatIOException().isThrownBy(() -> in.asInputStream().available());
    }

    @Test
    void closesThroughItsStreamView() throws IOException {
        var stream = new ChunkedInputStream(new byte[] {1, 2}, () -> 1);
        var in = ByteReader.of(stream);

        in.asInputStream().close();

        assertThat(stream.closeCount()).isOne();
        assertThatIOException().isThrownBy(in::readByte);
    }

    @Test
    void failsAfterClosingBytes() throws IOException {
        var in = ByteReader.of(Bytes.of(new byte[] {1, 2}));

        in.close();

        assertThatIOException().isThrownBy(in::readByte);
        assertThatIOException().isThrownBy(() -> in.readBytes(1));
    }

    @Test
    void rejectsNegativeCounts() {
        var in = ByteReader.of(Bytes.of(new byte[] {1}));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> in.request(-1))
                .withMessage("byteCount must not be negative: -1");
        assertThatIllegalArgumentException().isThrownBy(() -> in.require(-1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> in.indexOf((byte) 1, -1))
                .withMessage("maxBytes must not be negative: -1");
        assertThatIllegalArgumentException().isThrownBy(() -> in.indexOf(Bytes.empty(), -1));
        assertThatIllegalArgumentException().isThrownBy(() -> in.readBytes(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> in.readString(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> in.readLine(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> in.skip(-1));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> in.readFully(new byte[1], 1, 1));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> in.read(new byte[1], -1, 1));
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNulls() {
        var in = ByteReader.of(Bytes.of(new byte[] {1}));

        assertThatNullPointerException()
                .isThrownBy(() -> ByteReader.of((InputStream) null))
                .withMessage("in");
        assertThatNullPointerException()
                .isThrownBy(() -> ByteReader.of((Bytes) null))
                .withMessage("bytes");
        assertThatNullPointerException().isThrownBy(() -> in.startsWith(null)).withMessage("prefix");
        assertThatNullPointerException().isThrownBy(() -> in.indexOf(null, 1)).withMessage("target");
        assertThatNullPointerException()
                .isThrownBy(() -> in.readFully(null, 0, 0))
                .withMessage("b");
        assertThatNullPointerException().isThrownBy(() -> in.read(null, 0, 0)).withMessage("b");
        assertThatNullPointerException().isThrownBy(() -> in.transferTo(null)).withMessage("out");
        assertThatNullPointerException()
                .isThrownBy(() -> in.readString(1, null))
                .withMessage("charset");
        assertThatNullPointerException().isThrownBy(() -> in.readLine(1, null)).withMessage("charset");
    }

    @Test
    void returnsAnEmptyOptionalOnlyAtTheEnd() throws IOException {
        assertThat(ByteReader.of(Bytes.empty()).readLine(1)).isEqualTo(Optional.empty());
        assertThat(ByteReader.of(Bytes.encode("\n")).readLine(1)).hasValue("");
    }

    @FunctionalInterface
    private interface ByteReaderCall {
        void accept(ByteReader in) throws IOException;
    }
}
