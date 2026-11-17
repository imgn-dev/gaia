package be.imgn.gaia.benchmarks;

import be.imgn.gaia.core.io.ByteSink;
import be.imgn.gaia.core.io.ByteSource;
import be.imgn.gaia.core.io.Bytes;
import be.imgn.gaia.core.io.CharSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Reads a text file whole, as bytes and as text, reads it line by line, and copies it to another file, with gaia and
 * with the JDK. The files are in the temporary directory, which the operating system most likely caches.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(2)
public class FileBenchmark {

    /** The size of the file, in bytes. */
    @Param({"1024", "65536", "8388608"})
    public int size;

    /** The text of the file, as {@link Texts#text(int, String)} names it. */
    @Param({"ascii", "mixed"})
    public String kind = "";

    private Path directory = Path.of("");
    private Path file = Path.of("");
    private Path copy = Path.of("");

    /**
     * Writes the file.
     *
     * @throws IOException if the file cannot be written
     */
    @Setup
    public void setUp() throws IOException {
        directory = Files.createTempDirectory("gaia-benchmarks");
        file = Files.writeString(directory.resolve("text.txt"), Texts.text(size, kind));
        copy = directory.resolve("copy.txt");
    }

    /**
     * Deletes the files.
     *
     * @throws IOException if a file cannot be deleted
     */
    @TearDown
    public void tearDown() throws IOException {
        Files.deleteIfExists(copy);
        Files.delete(file);
        Files.delete(directory);
    }

    /**
     * Reads the file as bytes with {@link ByteSource#read()}.
     *
     * @return the bytes
     * @throws IOException if the file cannot be read
     */
    @Benchmark
    public Bytes bytesGaia() throws IOException {
        return ByteSource.of(file).read();
    }

    /**
     * Reads the file as bytes with {@link Files#readAllBytes(Path)}.
     *
     * @return the bytes
     * @throws IOException if the file cannot be read
     */
    @Benchmark
    public byte[] bytesJdk() throws IOException {
        return Files.readAllBytes(file);
    }

    /**
     * Reads the file as text with {@link CharSource#read()}.
     *
     * @return the text
     * @throws IOException if the file cannot be read
     */
    @Benchmark
    public String textGaia() throws IOException {
        return CharSource.of(file).read();
    }

    /**
     * Reads the file as text with {@link Files#readString(Path)}.
     *
     * @return the text
     * @throws IOException if the file cannot be read
     */
    @Benchmark
    public String textJdk() throws IOException {
        return Files.readString(file);
    }

    /**
     * Reads the lines of the file with {@link CharSource#forEachLine(java.util.function.Consumer)}.
     *
     * @param blackhole what takes the lines
     * @throws IOException if the file cannot be read
     */
    @Benchmark
    public void linesGaia(Blackhole blackhole) throws IOException {
        CharSource.of(file).forEachLine(blackhole::consume);
    }

    /**
     * Reads the lines of the file as bytes, decoding each line, with
     * {@link be.imgn.gaia.core.io.ByteReader#readLine(int, java.nio.charset.Charset)}.
     *
     * @param blackhole what takes the lines
     * @throws IOException if the file cannot be read
     */
    @Benchmark
    public void linesGaiaByteReader(Blackhole blackhole) throws IOException {
        try (var in = ByteSource.of(file).openReader()) {
            for (var line = in.readLine(Integer.MAX_VALUE, StandardCharsets.UTF_8);
                    line.isPresent();
                    line = in.readLine(Integer.MAX_VALUE, StandardCharsets.UTF_8)) {
                blackhole.consume(line.get());
            }
        }
    }

    /**
     * Reads the lines of the file with {@link java.io.BufferedReader#readLine()}.
     *
     * @param blackhole what takes the lines
     * @throws IOException if the file cannot be read
     */
    @Benchmark
    public void linesJdk(Blackhole blackhole) throws IOException {
        try (var in = Files.newBufferedReader(file)) {
            for (var line = in.readLine(); line != null; line = in.readLine()) {
                blackhole.consume(line);
            }
        }
    }

    /**
     * Copies the file with {@link ByteSource#copyTo(ByteSink)}.
     *
     * @return the number of bytes copied
     * @throws IOException if the file cannot be copied
     */
    @Benchmark
    public long copyGaia() throws IOException {
        return ByteSource.of(file).copyTo(ByteSink.replacing(copy));
    }

    /**
     * Copies the file with {@link java.io.InputStream#transferTo(java.io.OutputStream)}.
     *
     * @return the number of bytes copied
     * @throws IOException if the file cannot be copied
     */
    @Benchmark
    public long copyJdk() throws IOException {
        try (var in = Files.newInputStream(file);
                var out = Files.newOutputStream(copy)) {
            return in.transferTo(out);
        }
    }
}
