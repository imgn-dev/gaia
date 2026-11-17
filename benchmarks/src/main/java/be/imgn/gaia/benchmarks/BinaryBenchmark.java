package be.imgn.gaia.benchmarks;

import be.imgn.gaia.core.io.ByteReader;
import be.imgn.gaia.core.io.ByteWriter;
import be.imgn.gaia.core.io.Bytes;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Reads and writes 64 KB of binary data an {@code int} or a byte at a time, and searches a byte in it, with gaia and
 * with the JDK's {@link DataInputStream} and {@link DataOutputStream}.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(2)
public class BinaryBenchmark {

    private static final int SIZE = 65536;

    // Odd, so that the data goes through every byte value.
    private static final int STEP = 31;

    private byte[] bytes = new byte[0];
    private Bytes gaiaBytes = Bytes.empty();
    private ByteWriter gaiaWriter = ByteWriter.of(OutputStream.nullOutputStream());
    private DataOutputStream jdkWriter = new DataOutputStream(OutputStream.nullOutputStream());

    /** Generates the data, whose only {@code '\n'} is its last byte, and opens the writers, which write to nothing. */
    @Setup
    public void setUp() {
        bytes = new byte[SIZE];
        for (int i = 0; i < SIZE - 1; i++) {
            var b = (byte) (i * STEP);
            bytes[i] = b == '\n' ? 0 : b;
        }
        bytes[SIZE - 1] = '\n';
        gaiaBytes = Bytes.copyOf(bytes, 0, SIZE);
        gaiaWriter = ByteWriter.of(OutputStream.nullOutputStream());
        jdkWriter = new DataOutputStream(new BufferedOutputStream(OutputStream.nullOutputStream()));
    }

    /**
     * Reads the data as {@code int}s with a {@link ByteReader} of {@link Bytes}, which has no stream.
     *
     * @return the sum of the {@code int}s
     * @throws IOException never
     */
    @Benchmark
    public int readIntGaiaBytes() throws IOException {
        var in = ByteReader.of(gaiaBytes);
        var sum = 0;
        for (int i = 0; i < SIZE / Integer.BYTES; i++) {
            sum += in.readInt();
        }
        return sum;
    }

    /**
     * Reads the data as {@code int}s with a {@link ByteReader} of a stream.
     *
     * @return the sum of the {@code int}s
     * @throws IOException never
     */
    @Benchmark
    public int readIntGaiaStream() throws IOException {
        var in = ByteReader.of(new ByteArrayInputStream(bytes));
        var sum = 0;
        for (int i = 0; i < SIZE / Integer.BYTES; i++) {
            sum += in.readInt();
        }
        return sum;
    }

    /**
     * Reads the data as {@code int}s with a {@link DataInputStream} over a {@link BufferedInputStream}.
     *
     * @return the sum of the {@code int}s
     * @throws IOException never
     */
    @Benchmark
    public int readIntJdk() throws IOException {
        var in = new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes)));
        var sum = 0;
        for (int i = 0; i < SIZE / Integer.BYTES; i++) {
            sum += in.readInt();
        }
        return sum;
    }

    /**
     * Reads the data a byte at a time with a {@link ByteReader} of a stream.
     *
     * @return the sum of the bytes
     * @throws IOException never
     */
    @Benchmark
    public int readByteGaia() throws IOException {
        var in = ByteReader.of(new ByteArrayInputStream(bytes));
        var sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += in.readByte();
        }
        return sum;
    }

    /**
     * Reads the data a byte at a time with a {@link DataInputStream} over a {@link BufferedInputStream}.
     *
     * @return the sum of the bytes
     * @throws IOException never
     */
    @Benchmark
    public int readByteJdk() throws IOException {
        var in = new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes)));
        var sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += in.readByte();
        }
        return sum;
    }

    /**
     * Writes 64 KB of {@code int}s with a {@link ByteWriter}.
     *
     * @return the writer
     * @throws IOException never
     */
    @Benchmark
    public ByteWriter writeIntGaia() throws IOException {
        for (int i = 0; i < SIZE / Integer.BYTES; i++) {
            gaiaWriter.writeInt(i);
        }
        gaiaWriter.flush();
        return gaiaWriter;
    }

    /**
     * Writes 64 KB of {@code int}s with a {@link DataOutputStream} over a {@link BufferedOutputStream}.
     *
     * @return the stream
     * @throws IOException never
     */
    @Benchmark
    public DataOutputStream writeIntJdk() throws IOException {
        for (int i = 0; i < SIZE / Integer.BYTES; i++) {
            jdkWriter.writeInt(i);
        }
        jdkWriter.flush();
        return jdkWriter;
    }

    /**
     * Writes 64 KB a byte at a time with a {@link ByteWriter}.
     *
     * @return the writer
     * @throws IOException never
     */
    @Benchmark
    public ByteWriter writeByteGaia() throws IOException {
        for (int i = 0; i < SIZE; i++) {
            gaiaWriter.writeByte(i);
        }
        gaiaWriter.flush();
        return gaiaWriter;
    }

    /**
     * Writes 64 KB a byte at a time with a {@link DataOutputStream} over a {@link BufferedOutputStream}.
     *
     * @return the stream
     * @throws IOException never
     */
    @Benchmark
    public DataOutputStream writeByteJdk() throws IOException {
        for (int i = 0; i < SIZE; i++) {
            jdkWriter.writeByte(i);
        }
        jdkWriter.flush();
        return jdkWriter;
    }

    /**
     * Searches the last byte with {@link ByteReader#indexOf(byte, int)}.
     *
     * @return its index
     * @throws IOException never
     */
    @Benchmark
    public int indexOfGaiaReader() throws IOException {
        return ByteReader.of(gaiaBytes).indexOf((byte) '\n', Integer.MAX_VALUE);
    }

    /**
     * Searches the last byte with {@link Bytes#indexOf(byte)}.
     *
     * @return its index
     */
    @Benchmark
    public int indexOfGaiaBytes() {
        return gaiaBytes.indexOf((byte) '\n');
    }
}
