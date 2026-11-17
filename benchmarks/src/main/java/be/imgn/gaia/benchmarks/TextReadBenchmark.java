package be.imgn.gaia.benchmarks;

import be.imgn.gaia.core.io.ByteSource;
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

/**
 * Reads a text file whole into a {@link String}, decoding its bytes at once or as a stream, to tune the size up to
 * which {@link CharSource#read()} decodes them at once: where {@link #decodedAtOnce()} stops being faster than
 * {@link #decodedAsAStream()}, which depends on the processor's caches.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 4, time = 2)
@Fork(2)
public class TextReadBenchmark {

    /** The size of the file, in bytes. */
    @Param({"1048576", "2097152", "4194304", "8388608", "16777216"})
    public int size;

    /** The text of the file, as {@link Texts#text(int, String)} names it. */
    @Param({"ascii", "mixed"})
    public String kind = "";

    private Path directory = Path.of("");
    private Path file = Path.of("");

    /**
     * Writes the file.
     *
     * @throws IOException if the file cannot be written
     */
    @Setup
    public void setUp() throws IOException {
        directory = Files.createTempDirectory("gaia-benchmarks");
        file = Files.writeString(directory.resolve("text.txt"), Texts.text(size, kind));
    }

    /**
     * Deletes the file.
     *
     * @throws IOException if the file cannot be deleted
     */
    @TearDown
    public void tearDown() throws IOException {
        Files.delete(file);
        Files.delete(directory);
    }

    /**
     * Reads the file with {@link CharSource#read()}, which decodes it at once or as a stream depending on its size.
     *
     * @return the text
     * @throws IOException if the file cannot be read
     */
    @Benchmark
    public String read() throws IOException {
        return CharSource.of(file).read();
    }

    /**
     * Reads the bytes of the file whole, then decodes them at once.
     *
     * @return the text
     * @throws IOException if the file cannot be read
     */
    @Benchmark
    public String decodedAtOnce() throws IOException {
        return ByteSource.of(file).read().decode(StandardCharsets.UTF_8);
    }

    /**
     * Decodes the bytes of the file as a stream, into a {@link StringBuilder}.
     *
     * @return the text
     * @throws IOException if the file cannot be read
     */
    @Benchmark
    public String decodedAsAStream() throws IOException {
        var text = new StringBuilder();
        CharSource.of(file).copyTo(text);
        return text.toString();
    }

    /**
     * Reads the file with {@link Files#readString(Path)}.
     *
     * @return the text
     * @throws IOException if the file cannot be read
     */
    @Benchmark
    public String jdk() throws IOException {
        return Files.readString(file);
    }
}
