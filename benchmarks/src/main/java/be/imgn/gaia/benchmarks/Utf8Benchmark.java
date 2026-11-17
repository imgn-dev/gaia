package be.imgn.gaia.benchmarks;

import be.imgn.gaia.core.io.ByteReader;
import be.imgn.gaia.core.io.ByteSink;
import be.imgn.gaia.core.io.ByteSource;
import be.imgn.gaia.core.io.ByteWriter;
import be.imgn.gaia.core.io.Bytes;
import be.imgn.gaia.core.io.CharSource;
import be.imgn.gaia.core.io.CharWriter;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Encodes text to UTF-8, decodes it, and reads its lines, in memory, with gaia and with the JDK: whole, as
 * {@link Bytes} and {@link String} do, and as a stream, through readers and writers.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(2)
public class Utf8Benchmark {

    /** The size of the text in UTF-8, in bytes. */
    @Param({"1024", "65536", "8388608"})
    public int size;

    /** The text, as {@link Texts#text(int, String)} names it. */
    @Param({"ascii", "mixed"})
    public String kind = "";

    private String text = "";
    private byte[] bytes = new byte[0];
    private Bytes gaiaBytes = Bytes.empty();
    private ByteWriter byteWriter = ByteWriter.of(OutputStream.nullOutputStream());
    private CharWriter charWriter = CharWriter.of(new StringWriter());
    private Writer jdkWriter = Writer.nullWriter();

    /**
     * Generates the text, and opens the writers, which write to nothing.
     *
     * @throws IOException never
     */
    @Setup
    public void setUp() throws IOException {
        text = Texts.text(size, kind);
        bytes = text.getBytes(StandardCharsets.UTF_8);
        gaiaBytes = Bytes.copyOf(bytes, 0, bytes.length);
        byteWriter = ByteWriter.of(OutputStream.nullOutputStream());
        ByteSink sink = () -> ByteWriter.of(OutputStream.nullOutputStream());
        charWriter = sink.asCharSink(StandardCharsets.UTF_8).openWriter();
        jdkWriter = new OutputStreamWriter(OutputStream.nullOutputStream(), StandardCharsets.UTF_8);
    }

    /**
     * Encodes the text with {@link Bytes#encode(CharSequence, java.nio.charset.Charset)}.
     *
     * @return the bytes
     */
    @Benchmark
    public Bytes encodeGaiaBytes() {
        return Bytes.encode(text, StandardCharsets.UTF_8);
    }

    /**
     * Encodes the text with {@link ByteWriter#writeString(CharSequence, java.nio.charset.Charset)}.
     *
     * @return the writer
     * @throws IOException never
     */
    @Benchmark
    public ByteWriter encodeGaiaByteWriter() throws IOException {
        byteWriter.writeString(text, StandardCharsets.UTF_8);
        byteWriter.flush();
        return byteWriter;
    }

    /**
     * Encodes the text with the {@link CharWriter} of {@link ByteSink#asCharSink(java.nio.charset.Charset)}.
     *
     * @return the writer
     * @throws IOException never
     */
    @Benchmark
    public CharWriter encodeGaiaCharWriter() throws IOException {
        charWriter.write(text);
        charWriter.flush();
        return charWriter;
    }

    /**
     * Encodes the text with {@link String#getBytes(java.nio.charset.Charset)}.
     *
     * @return the bytes
     */
    @Benchmark
    public byte[] encodeJdk() {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encodes the text with an {@link OutputStreamWriter}.
     *
     * @return the writer
     * @throws IOException never
     */
    @Benchmark
    public Writer encodeJdkWriter() throws IOException {
        jdkWriter.write(text);
        jdkWriter.flush();
        return jdkWriter;
    }

    /**
     * Decodes the bytes with {@link ByteReader#readString(int, java.nio.charset.Charset)}.
     *
     * @return the text
     * @throws IOException never
     */
    @Benchmark
    public String decodeGaiaByteReader() throws IOException {
        return ByteReader.of(gaiaBytes).readString(bytes.length, StandardCharsets.UTF_8);
    }

    /**
     * Decodes the bytes with the {@link CharSource} of {@link ByteSource#asCharSource(java.nio.charset.Charset)}.
     *
     * @return the text
     * @throws IOException never
     */
    @Benchmark
    public String decodeGaiaCharSource() throws IOException {
        return ByteSource.wrap(bytes).asCharSource(StandardCharsets.UTF_8).read();
    }

    /**
     * Decodes the bytes with {@link String#String(byte[], java.nio.charset.Charset)}.
     *
     * @return the text
     */
    @Benchmark
    public String decodeJdk() {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Decodes the bytes with an {@link InputStreamReader}.
     *
     * @return the text
     * @throws IOException never
     */
    @Benchmark
    public String decodeJdkReader() throws IOException {
        var out = new StringWriter(bytes.length);
        new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8).transferTo(out);
        return out.toString();
    }

    /**
     * Reads the lines of the bytes with the {@link CharSource} of
     * {@link ByteSource#asCharSource(java.nio.charset.Charset)}.
     *
     * @param blackhole what takes the lines
     * @throws IOException never
     */
    @Benchmark
    public void linesGaiaCharSource(Blackhole blackhole) throws IOException {
        ByteSource.wrap(bytes).asCharSource(StandardCharsets.UTF_8).forEachLine(blackhole::consume);
    }

    /**
     * Reads the lines of the bytes with {@link ByteReader#readLine(int, java.nio.charset.Charset)}.
     *
     * @param blackhole what takes the lines
     * @throws IOException never
     */
    @Benchmark
    public void linesGaiaByteReader(Blackhole blackhole) throws IOException {
        var in = ByteReader.of(gaiaBytes);
        for (var line = in.readLine(Integer.MAX_VALUE, StandardCharsets.UTF_8);
                line.isPresent();
                line = in.readLine(Integer.MAX_VALUE, StandardCharsets.UTF_8)) {
            blackhole.consume(line.get());
        }
    }

    /**
     * Reads the lines of the bytes with a {@link BufferedReader} over an {@link InputStreamReader}.
     *
     * @param blackhole what takes the lines
     * @throws IOException never
     */
    @Benchmark
    public void linesJdk(Blackhole blackhole) throws IOException {
        var in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
        for (var line = in.readLine(); line != null; line = in.readLine()) {
            blackhole.consume(line);
        }
    }

    /**
     * Reads the lines of the text with {@link CharSource#forEachLine(java.util.function.Consumer)}, with no decoding.
     *
     * @param blackhole what takes the lines
     * @throws IOException never
     */
    @Benchmark
    public void stringLinesGaia(Blackhole blackhole) throws IOException {
        CharSource.wrap(text).forEachLine(blackhole::consume);
    }

    /**
     * Reads the lines of the text with a {@link BufferedReader} over a {@link StringReader}, with no decoding.
     *
     * @param blackhole what takes the lines
     * @throws IOException never
     */
    @Benchmark
    public void stringLinesJdk(Blackhole blackhole) throws IOException {
        var in = new BufferedReader(new StringReader(text));
        for (var line = in.readLine(); line != null; line = in.readLine()) {
            blackhole.consume(line);
        }
    }
}
