package be.imgn.gaia.core.io;

import static be.imgn.gaia.core.io.TestStreams.eachChunking;
import static be.imgn.gaia.core.io.TestStreams.randomBytes;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.io.TestStreams.ChunkedInputStream;
import be.imgn.gaia.core.io.TestStreams.FailingOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ByteWriterTest {

    // More than three buffers, so that writes cross the end of the buffer.
    private static final int SIZE = 30_000;

    @Test
    void writesBigEndianNumbersAsDataOutputStreamDoes() throws IOException {
        var random = new SplittableRandom(1);
        var expected = new ByteArrayOutputStream();
        var data = new DataOutputStream(expected);
        var actual = new ByteArrayOutputStream();
        try (var out = ByteWriter.of(actual)) {
            while (data.size() < SIZE) {
                switch (random.nextInt(6)) {
                    case 0 -> {
                        var value = random.nextInt();
                        out.writeByte(value);
                        data.writeByte(value);
                    }
                    case 1 -> {
                        var value = random.nextInt();
                        out.writeShort(value);
                        data.writeShort(value);
                    }
                    case 2 -> {
                        var value = random.nextInt();
                        out.writeInt(value);
                        data.writeInt(value);
                    }
                    case 3 -> {
                        var value = random.nextLong();
                        out.writeLong(value);
                        data.writeLong(value);
                    }
                    case 4 -> {
                        var value = Float.intBitsToFloat(random.nextInt());
                        out.writeFloat(value);
                        data.writeFloat(value);
                    }
                    default -> {
                        var value = Double.longBitsToDouble(random.nextLong());
                        out.writeDouble(value);
                        data.writeDouble(value);
                    }
                }
            }
        }
        assertThat(actual.toByteArray()).isEqualTo(expected.toByteArray());
    }

    @Test
    void writesLittleEndianNumbers() throws IOException {
        var random = new SplittableRandom(2);
        var expected = ByteBuffer.allocate(SIZE + Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        var actual = new ByteArrayOutputStream();
        try (var out = ByteWriter.of(actual)) {
            while (expected.position() < SIZE) {
                switch (random.nextInt(5)) {
                    case 0 -> {
                        var value = random.nextInt();
                        out.writeShortLe(value);
                        expected.putShort((short) value);
                    }
                    case 1 -> {
                        var value = random.nextInt();
                        out.writeIntLe(value);
                        expected.putInt(value);
                    }
                    case 2 -> {
                        var value = random.nextLong();
                        out.writeLongLe(value);
                        expected.putLong(value);
                    }
                    case 3 -> {
                        var value = Float.intBitsToFloat(random.nextInt());
                        out.writeFloatLe(value);
                        expected.putInt(Float.floatToIntBits(value));
                    }
                    default -> {
                        var value = Double.longBitsToDouble(random.nextLong());
                        out.writeDoubleLe(value);
                        expected.putLong(Double.doubleToLongBits(value));
                    }
                }
            }
        }
        assertThat(actual.toByteArray()).isEqualTo(Arrays.copyOf(expected.array(), expected.position()));
    }

    @Test
    void writesRunsOfBytesInOrder() throws IOException {
        var bytes = randomBytes(3, SIZE);
        var actual = new ByteArrayOutputStream();
        try (var out = ByteWriter.of(actual)) {
            out.writeByte(1)
                    .write(Arrays.copyOf(bytes, 10))
                    // Larger than the buffer.
                    .write(bytes, 10, 20_000)
                    .writeByte(2)
                    .write(Bytes.copyOf(bytes, 20_010, 100))
                    .write(Bytes.copyOf(bytes, 20_110, SIZE - 20_110))
                    .write(new byte[0]);
        }
        var expected = new ByteArrayOutputStream();
        expected.write(1);
        expected.write(bytes, 0, 20_010);
        expected.write(2);
        expected.write(bytes, 20_010, SIZE - 20_010);
        assertThat(actual.toByteArray()).isEqualTo(expected.toByteArray());
    }

    @Test
    void writesText() throws IOException {
        var text = "héllo \uD800 😀 " + "x".repeat(20_000);
        var actual = new ByteArrayOutputStream();
        try (var out = ByteWriter.of(actual)) {
            out.writeString(text).writeString(CharBuffer.wrap(text), ISO_8859_1);
        }
        var expected = new ByteArrayOutputStream();
        expected.write(text.getBytes(UTF_8));
        expected.write(text.getBytes(ISO_8859_1));
        assertThat(actual.toByteArray()).isEqualTo(expected.toByteArray());
    }

    @Test
    void transfersAStream() throws IOException {
        var bytes = randomBytes(4, SIZE);
        for (var in : eachChunking(chunk -> new ChunkedInputStream(bytes, chunk))) {
            var actual = new ByteArrayOutputStream();
            try (var out = ByteWriter.of(actual)) {
                out.writeByte(9);
                assertThat(out.transferFrom(in.value())).as(in.name()).isEqualTo(SIZE);
            }
            assertThat(actual.toByteArray()[0]).as(in.name()).isEqualTo((byte) 9);
            assertThat(Arrays.copyOfRange(actual.toByteArray(), 1, SIZE + 1))
                    .as(in.name())
                    .isEqualTo(bytes);
            assertThat(in.value().closeCount()).as(in.name()).isZero();
        }
    }

    @Test
    void buffersUntilFlushed() throws IOException {
        var stream = new FlushCountingOutputStream();
        var out = ByteWriter.of(stream);

        out.writeInt(1);
        assertThat(stream.size()).isZero();

        out.flush();
        assertThat(stream.size()).isEqualTo(4);
        assertThat(stream.flushCount()).isOne();
    }

    @Test
    void buffersSmallWritesIntoFewStreamWrites() throws IOException {
        var stream = new FlushCountingOutputStream();
        try (var out = ByteWriter.of(stream)) {
            for (var i = 0; i < SIZE; i++) {
                out.writeByte(i);
            }
        }

        assertThat(stream.size()).isEqualTo(SIZE);
        // A buffer of 8 KiB, written whole each time it is full, and once more on closing.
        assertThat(stream.writeCount()).isEqualTo(SIZE / 8192 + 1);
    }

    @Test
    void writesAroundTheSizeOfTheBuffer() throws IOException {
        for (var size : new int[] {8191, 8192, 8193}) {
            var bytes = randomBytes(size, size);
            var stream = new ByteArrayOutputStream();
            try (var out = ByteWriter.of(stream)) {
                out.write(bytes).writeInt(size).write(bytes);
            }

            var expected =
                    ByteBuffer.allocate(2 * size + 4).put(bytes).putInt(size).put(bytes);
            assertThat(stream.toByteArray()).as("%s bytes", size).isEqualTo(expected.array());
        }
    }

    @Test
    void closesWithoutHavingWritten() throws IOException {
        var stream = new FlushCountingOutputStream();
        var out = ByteWriter.of(stream);

        out.flush();
        out.close();

        assertThat(stream.size()).isZero();
        assertThat(stream.writeCount()).isZero();
        assertThat(stream.flushCount()).isOne();
        assertThat(stream.closeCount()).isOne();
        assertThatIOException().isThrownBy(() -> out.writeByte(1)).withMessage("Writer closed");
        assertThatIOException().isThrownBy(() -> out.writeLong(1)).withMessage("Writer closed");
        assertThatIOException().isThrownBy(() -> out.write(new byte[1])).withMessage("Writer closed");
        assertThatIOException().isThrownBy(() -> out.write(new byte[10_000])).withMessage("Writer closed");
        assertThatIOException().isThrownBy(() -> out.writeString("a")).withMessage("Writer closed");
    }

    @Test
    void encodesTextFromTheStart() throws IOException {
        var text = "é😀a".repeat(5000);
        var stream = new ByteArrayOutputStream();
        try (var out = CharWriter.of(new EncodingWriter(ByteWriter.of(stream), UTF_8.newEncoder()))) {
            out.write(text);
        }

        assertThat(stream.toString(UTF_8)).isEqualTo(text);
    }

    @Test
    void flushesAndClosesItsStreamOnce() throws IOException {
        var stream = new FlushCountingOutputStream();
        var out = ByteWriter.of(stream);
        out.writeInt(1);

        out.close();
        out.close();

        assertThat(stream.toByteArray()).containsExactly(0, 0, 0, 1);
        assertThat(stream.closeCount()).isOne();
        assertThatIOException().isThrownBy(() -> out.writeByte(1)).withMessage("Writer closed");
        assertThatIOException().isThrownBy(() -> out.write(new byte[1]));
        assertThatIOException().isThrownBy(() -> out.writeString("a"));
        assertThatIOException().isThrownBy(out::flush);
        assertThatIOException().isThrownBy(() -> out.transferFrom(new ChunkedInputStream(new byte[1], () -> 1)));
        assertThatIOException().isThrownBy(() -> out.asOutputStream().write(1));
    }

    @Test
    void closesItsStreamEvenIfFlushingFails() throws IOException {
        var stream = new FailingOutputStream();
        var out = ByteWriter.of(stream);
        out.writeByte(1);

        assertThatIOException()
                .isThrownBy(out::close)
                .withMessage("write")
                .satisfies(e -> assertThat(e.getSuppressed())
                        .singleElement()
                        .extracting(Throwable::getMessage)
                        .isEqualTo("close"));
        assertThat(stream.closeCount()).isOne();

        out.close();
        assertThat(stream.closeCount()).isOne();
    }

    @Test
    void unwrapsItsStreamView() {
        var out = ByteWriter.of(OutputStream.nullOutputStream());

        assertThat(ByteWriter.of(out.asOutputStream())).isSameAs(out);
    }

    @Test
    void sharesItsBufferWithItsStreamView() throws IOException {
        var stream = new FlushCountingOutputStream();
        var out = ByteWriter.of(stream);
        var view = out.asOutputStream();

        out.writeByte(1);
        view.write(2);
        out.writeByte(3);
        view.write(new byte[] {4, 5}, 0, 2);
        view.flush();
        assertThat(stream.toByteArray()).containsExactly(1, 2, 3, 4, 5);
        assertThat(stream.flushCount()).isOne();

        view.close();
        assertThat(stream.closeCount()).isOne();
    }

    @Test
    void rejectsInvalidRanges() {
        var out = ByteWriter.of(new ByteArrayOutputStream());

        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> out.write(new byte[1], 1, 1));
        assertThatExceptionOfType(IndexOutOfBoundsException.class).isThrownBy(() -> out.write(new byte[1], -1, 1));
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNulls() {
        var out = ByteWriter.of(new ByteArrayOutputStream());

        assertThatNullPointerException().isThrownBy(() -> ByteWriter.of(null)).withMessage("out");
        assertThatNullPointerException()
                .isThrownBy(() -> out.write((byte[]) null))
                .withMessage("b");
        assertThatNullPointerException().isThrownBy(() -> out.write(null, 0, 0)).withMessage("b");
        assertThatNullPointerException()
                .isThrownBy(() -> out.write((Bytes) null))
                .withMessage("bytes");
        assertThatNullPointerException().isThrownBy(() -> out.writeString(null)).withMessage("chars");
        assertThatNullPointerException()
                .isThrownBy(() -> out.writeString("a", null))
                .withMessage("charset");
        assertThatNullPointerException()
                .isThrownBy(() -> out.transferFrom(null))
                .withMessage("in");
    }

    private static final class FlushCountingOutputStream extends ByteArrayOutputStream {

        private int flushCount;
        private int writeCount;
        private int closeCount;

        @Override
        public void flush() {
            flushCount++;
        }

        @Override
        public void close() {
            closeCount++;
        }

        @Override
        public synchronized void write(byte[] b, int offset, int length) {
            writeCount++;
            super.write(b, offset, length);
        }

        int writeCount() {
            return writeCount;
        }

        int flushCount() {
            return flushCount;
        }

        int closeCount() {
            return closeCount;
        }
    }
}
