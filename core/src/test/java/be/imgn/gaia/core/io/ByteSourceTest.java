package be.imgn.gaia.core.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import be.imgn.gaia.core.base.Charsets;
import be.imgn.gaia.core.io.TestStreams.ChunkedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ByteSourceTest {

    private static final byte[] BYTES = {1, 2, 3, 4, 5};

    @TempDir
    Path directory;

    private static byte[] bytes(int count) {
        var bytes = new byte[count];
        for (var i = 0; i < count; i++) {
            bytes[i] = (byte) i;
        }
        return bytes;
    }

    @Test
    void readsAWrappedArray() throws IOException {
        var source = ByteSource.wrap(BYTES);

        assertThat(source.read().toByteArray()).containsExactly(1, 2, 3, 4, 5);
        assertThat(source.sizeIfKnown()).hasValue(5);
        assertThat(source.isEmpty()).isFalse();
    }

    @Test
    void readsTheCurrentContentOfAWrappedArray() throws IOException {
        var bytes = new byte[] {1, 2};
        var source = ByteSource.wrap(bytes);

        bytes[0] = 9;

        assertThat(source.read().toByteArray()).containsExactly(9, 2);
    }

    @Test
    void readsNothingFromTheEmptySource() throws IOException {
        var source = ByteSource.empty();

        assertThat(source.read().isEmpty()).isTrue();
        assertThat(source.size()).isZero();
        assertThat(source.isEmpty()).isTrue();
    }

    @Test
    void readsALambda() throws IOException {
        ByteSource source = () -> ByteReader.of(new ByteArrayInputStream(BYTES));

        assertThat(source.read().toByteArray()).containsExactly(1, 2, 3, 4, 5);
        assertThat(source.sizeIfKnown()).isEmpty();
        assertThat(source.size()).isEqualTo(5);
        assertThat(source.isEmpty()).isFalse();
        assertThat(((ByteSource) () -> ByteReader.of(new ByteArrayInputStream(new byte[0]))).isEmpty())
                .isTrue();
    }

    @Test
    void closesTheStreamsItOpens() throws IOException {
        var source = new TrackedSource(BYTES);

        source.read();
        source.size();
        source.isEmpty();
        source.copyTo(new ByteArrayOutputStream());
        source.contentEquals(ByteSource.wrap(BYTES));

        assertThat(source.opened).isEqualTo(5);
        assertThat(source.closed).isEqualTo(5);
    }

    @Test
    void readsAFile() throws IOException {
        var file = Files.write(directory.resolve("data"), BYTES);
        var source = ByteSource.of(file);

        assertThat(source.read().toByteArray()).containsExactly(1, 2, 3, 4, 5);
        assertThat(source.sizeIfKnown()).hasValue(5);
    }

    @Test
    void doesNotKnowTheSizeOfAMissingFile() {
        var source = ByteSource.of(directory.resolve("missing"));

        assertThat(source.sizeIfKnown()).isEmpty();
        assertThatExceptionOfType(NoSuchFileException.class).isThrownBy(source::read);
    }

    @Test
    void doesNotKnowTheSizeOfADirectory() {
        assertThat(ByteSource.of(directory).sizeIfKnown()).isEmpty();
    }

    @Test
    void readsAUrl() throws IOException {
        var file = Files.write(directory.resolve("data"), BYTES);

        assertThat(ByteSource.of(file.toUri().toURL()).read().toByteArray()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void readsAUri() throws IOException {
        var file = Files.write(directory.resolve("data"), BYTES);

        assertThat(ByteSource.of(file.toUri()).read().toByteArray()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void rejectsAUriThatIsNotAUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> ByteSource.of(URI.create("relative/path")));
        assertThatIllegalArgumentException().isThrownBy(() -> ByteSource.of(URI.create("unknown:path")));
    }

    @Test
    void copiesToAStream() throws IOException {
        var out = new ByteArrayOutputStream();

        assertThat(ByteSource.wrap(BYTES).copyTo(out)).isEqualTo(5);
        assertThat(out.toByteArray()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void copiesToASink() throws IOException {
        var out = new ByteArrayOutputStream();

        assertThat(ByteSource.wrap(BYTES).copyTo(() -> ByteWriter.of(out))).isEqualTo(5);
        assertThat(out.toByteArray()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void comparesContent() throws IOException {
        var large = bytes(20_000);
        var changed = large.clone();
        changed[19_999] = 0;
        ByteSource unknownSize = () -> ByteReader.of(new ByteArrayInputStream(large));

        assertThat(ByteSource.wrap(large).contentEquals(unknownSize)).isTrue();
        assertThat(ByteSource.wrap(changed).contentEquals(unknownSize)).isFalse();
        assertThat(ByteSource.wrap(Arrays.copyOf(large, 19_999)).contentEquals(unknownSize))
                .isFalse();
        assertThat(ByteSource.empty().contentEquals(() -> ByteReader.of(new ByteArrayInputStream(new byte[0]))))
                .isTrue();
    }

    @Test
    void comparesKnownSizesWithoutReading() throws IOException {
        var unreadable = new ByteSource() {
            @Override
            public ByteReader openReader() throws IOException {
                throw new IOException("unreadable");
            }

            @Override
            public OptionalLong sizeIfKnown() {
                return OptionalLong.of(4);
            }
        };

        assertThat(ByteSource.wrap(BYTES).contentEquals(unreadable)).isFalse();
    }

    @Test
    void readsASlice() throws IOException {
        var source = ByteSource.wrap(BYTES).slice(1, 3);

        assertThat(source.read().toByteArray()).containsExactly(2, 3, 4);
        assertThat(source.sizeIfKnown()).hasValue(3);
    }

    @Test
    void readsASliceThatGoesPastTheEnd() throws IOException {
        var source = ByteSource.wrap(BYTES).slice(3, 10);

        assertThat(source.read().toByteArray()).containsExactly(4, 5);
        assertThat(source.sizeIfKnown()).hasValue(2);
    }

    @Test
    void readsNothingFromASliceThatStartsPastTheEnd() throws IOException {
        var source = ByteSource.wrap(BYTES).slice(10, 2);

        assertThat(source.read().isEmpty()).isTrue();
        assertThat(source.sizeIfKnown()).hasValue(0);
        assertThat(source.isEmpty()).isTrue();
    }

    @Test
    void readsASliceOfALargeStreamByteByByte() throws IOException {
        var large = bytes(20_000);
        ByteSource source = () -> ByteReader.of(new ByteArrayInputStream(large));
        var slice = source.slice(10_000, 3);

        assertThat(slice.sizeIfKnown()).isEmpty();
        try (var in = slice.openReader()) {
            assertThat(in.readByte()).isEqualTo(large[10_000]);
            assertThat(in.readByte()).isEqualTo(large[10_001]);
            assertThat(in.readByte()).isEqualTo(large[10_002]);
            assertThat(in.exhausted()).isTrue();
        }
    }

    @Test
    void doesNotSkipBackwardsInASlice() throws IOException {
        var file = Files.write(directory.resolve("bytes"), bytes(16));
        ByteSource source = () -> ByteReader.of(new FileInputStream(file.toFile()));

        try (var in = source.slice(8, 3).openReader().asInputStream()) {
            assertThat(in.skip(-8)).isZero();
            assertThat(in.readAllBytes()).containsExactly(8, 9, 10);
        }
    }

    @Test
    void readsASliceOfASlice() throws IOException {
        assertThat(ByteSource.wrap(BYTES).slice(1, 3).slice(1, 5).read().toByteArray())
                .containsExactly(3, 4);
    }

    @Test
    void rejectsANegativeSlice() {
        var source = ByteSource.wrap(BYTES);

        assertThatIllegalArgumentException().isThrownBy(() -> source.slice(-1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> source.slice(1, -1));
    }

    @Test
    void readsConcatenatedSources() throws IOException {
        var source = ByteSource.concat(ByteSource.wrap(new byte[] {1, 2}), ByteSource.empty(), ByteSource.wrap(BYTES));

        assertThat(source.read().toByteArray()).containsExactly(1, 2, 1, 2, 3, 4, 5);
        assertThat(source.sizeIfKnown()).hasValue(7);
        assertThat(source.isEmpty()).isFalse();
    }

    @Test
    void readsConcatenatedSourcesByteByByte() throws IOException {
        var source = ByteSource.concat(ByteSource.wrap(new byte[] {1}), ByteSource.empty(), ByteSource.wrap(BYTES));

        var read = new ArrayList<Integer>();
        try (var in = source.openReader()) {
            while (!in.exhausted()) {
                read.add(in.readUnsignedByte());
            }
        }

        assertThat(read).containsExactly(1, 1, 2, 3, 4, 5);
    }

    @Test
    void opensEachConcatenatedSourceWhenItsTurnComes() throws IOException {
        var first = new TrackedSource(new byte[] {1});
        var second = new TrackedSource(new byte[] {2});

        try (var in = ByteSource.concat(first, second).openReader()) {
            assertThat(second.opened).isZero();
            in.readByte();
            in.readByte();

            assertThat(first.closed).isEqualTo(1);
            assertThat(second.opened).isEqualTo(1);
        }
        assertThat(second.closed).isEqualTo(1);
    }

    @Test
    void doesNotKnowTheSizeOfConcatenatedSourcesIfOneIsUnknown() throws IOException {
        var source = ByteSource.concat(ByteSource.wrap(BYTES), () -> ByteReader.of(new ByteArrayInputStream(BYTES)));

        assertThat(source.sizeIfKnown()).isEmpty();
        assertThat(source.size()).isEqualTo(10);
    }

    @Test
    void readsNothingFromNoConcatenatedSources() throws IOException {
        var source = ByteSource.concat();

        assertThat(source.read().isEmpty()).isTrue();
        assertThat(source.isEmpty()).isTrue();
        assertThat(ByteSource.concat(ByteSource.empty(), ByteSource.empty()).isEmpty())
                .isTrue();
    }

    @Test
    void copiesTheIterableOfConcatenatedSources() throws IOException {
        var sources = new ArrayList<ByteSource>(List.of(ByteSource.wrap(BYTES)));
        var source = ByteSource.concat(sources);

        sources.add(ByteSource.wrap(BYTES));

        assertThat(source.read().toByteArray()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void decodesCharacters() throws IOException {
        var source = ByteSource.wrap("café ✓".getBytes(StandardCharsets.UTF_8));

        assertThat(source.asCharSource(StandardCharsets.UTF_8).read()).isEqualTo("café ✓");
    }

    @Test
    void decodesCharactersInTheDefaultCharset() throws IOException {
        var source = ByteSource.wrap("café ✓".getBytes(Charsets.DEFAULT_CHARSET));

        assertThat(source.asCharSource().read()).isEqualTo("café ✓");
    }

    @Test
    void decodesCharactersBeyondABuffer() throws IOException {
        var text = "aé😀".repeat(10_000);
        ByteSource source = () -> ByteReader.of(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));

        assertThat(source.asCharSource(StandardCharsets.UTF_8).read()).isEqualTo(text);
    }

    @Test
    void decodesASurrogatePairForRoomForOneCharacter() throws IOException {
        var bytes = ByteReader.of(Bytes.of("😀".getBytes(StandardCharsets.UTF_8)));

        try (var in = new DecodingReader(bytes, StandardCharsets.UTF_8.newDecoder())) {
            var c = new char[1];
            assertThat(in.read(c)).isEqualTo(1);
            assertThat(c[0]).isEqualTo('\uD83D');
            assertThat(in.read(c)).isEqualTo(1);
            assertThat(c[0]).isEqualTo('\uDE00');
            assertThat(in.read(c)).isEqualTo(-1);
        }
    }

    @Test
    void failsOnBytesThatAreNotValidInTheCharset() {
        var source = ByteSource.wrap(new byte[] {'a', (byte) 0xFF, 'b'}).asCharSource(StandardCharsets.UTF_8);

        assertThatExceptionOfType(MalformedInputException.class).isThrownBy(source::read);
    }

    @Test
    void readsTheCharactersBeforeInvalidBytes() throws IOException {
        var source = ByteSource.wrap(new byte[] {'a', 'b', (byte) 0xFF}).asCharSource(StandardCharsets.UTF_8);

        try (var in = source.openReader()) {
            assertThat(in.readString(2)).isEqualTo("ab");
            assertThatExceptionOfType(MalformedInputException.class).isThrownBy(() -> in.readString(1));
        }
    }

    // Valid text, and invalid bytes of every kind, some of them past a buffer, so that a reader reads them in several
    // chunks.
    private static List<byte[]> utf8Inputs() {
        var inputs = new ArrayList<byte[]>();
        for (var text : List.of(
                "",
                "café ✓ 😀",
                "a�b",
                "�",
                "a".repeat(8191) + "é😀" + "b".repeat(8190) + "✓",
                "aé😀�".repeat(30_000))) {
            inputs.add(text.getBytes(StandardCharsets.UTF_8));
        }
        var ascii = "a".repeat(9000).getBytes(StandardCharsets.US_ASCII);
        for (var invalid : List.of(
                new byte[] {'a', (byte) 0xFF, 'b'},
                new byte[] {'a', (byte) 0xE2, (byte) 0x82},
                new byte[] {(byte) 0xC0, (byte) 0x80},
                new byte[] {(byte) 0xED, (byte) 0xA0, (byte) 0x80},
                new byte[] {(byte) 0xE2, 0x28, (byte) 0xA1},
                new byte[] {(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80},
                new byte[] {(byte) 0xF0, (byte) 0x9F, (byte) 0x98})) {
            inputs.add(invalid);
            var late = Arrays.copyOf(ascii, ascii.length + invalid.length);
            System.arraycopy(invalid, 0, late, ascii.length, invalid.length);
            inputs.add(late);
        }
        return inputs;
    }

    // What decoding the bytes as a stream gives: the text, or the class and message of the exception.
    private static String decodedAsAStream(byte[] bytes, Charset charset) {
        ByteSource source = () -> ByteReader.of(new ChunkedInputStream(bytes, () -> 1000));
        try (var in = source.asCharSource(charset).openReader()) {
            return "text " + in.readAll();
        } catch (IOException e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    private static String readWhole(CharSource source) {
        try {
            return "text " + source.read();
        } catch (IOException e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }

    // Sources of the bytes of each kind: an array, a stream that returns them a few at a time, and a file.
    private List<ByteSource> sourcesOf(byte[] bytes) throws IOException {
        var file = Files.write(Files.createTempFile(directory, "text", ".txt"), bytes);
        return List.of(
                ByteSource.wrap(bytes),
                () -> ByteReader.of(new ChunkedInputStream(bytes, () -> 7)),
                ByteSource.of(file));
    }

    @Test
    void readsTextWholeAsItDecodesItAsAStream() throws IOException {
        for (var bytes : utf8Inputs()) {
            var expected = decodedAsAStream(bytes, StandardCharsets.UTF_8);
            for (var source : sourcesOf(bytes)) {
                assertThat(readWhole(source.asCharSource(StandardCharsets.UTF_8)))
                        .as("%s bytes from %s", bytes.length, source)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void readsTextWholeInOtherCharsetsAsItDecodesItAsAStream() throws IOException {
        var latin1 = new byte[256];
        for (var i = 0; i < latin1.length; i++) {
            latin1[i] = (byte) i;
        }
        var utf16 = "café 😀".getBytes(StandardCharsets.UTF_16);
        var inputs = List.of(
                Map.entry(StandardCharsets.ISO_8859_1, latin1),
                Map.entry(StandardCharsets.US_ASCII, "abc".getBytes(StandardCharsets.US_ASCII)),
                Map.entry(StandardCharsets.US_ASCII, new byte[] {'a', (byte) 0x80}),
                Map.entry(StandardCharsets.UTF_16, utf16),
                Map.entry(StandardCharsets.UTF_16, Arrays.copyOf(utf16, utf16.length - 1)),
                Map.entry(StandardCharsets.UTF_16LE, "a".repeat(9000).getBytes(StandardCharsets.UTF_16LE)),
                Map.entry(Charset.forName("windows-1252"), new byte[] {'a', (byte) 0x80, (byte) 0x81}));
        for (var input : inputs) {
            var expected = decodedAsAStream(input.getValue(), input.getKey());
            for (var source : sourcesOf(input.getValue())) {
                assertThat(readWhole(source.asCharSource(input.getKey())))
                        .as("%s from %s", input.getKey(), source)
                        .isEqualTo(expected);
            }
        }
        assertThat(decodedAsAStream(new byte[] {'a', (byte) 0x80}, StandardCharsets.US_ASCII))
                .startsWith(MalformedInputException.class.getName());
        assertThat(decodedAsAStream(new byte[] {'a', (byte) 0x81}, Charset.forName("windows-1252")))
                .startsWith(UnmappableCharacterException.class.getName());
    }

    @Test
    void readsTextAboveTheWholeLimitAsItDecodesItAsAStream() throws IOException {
        var limit = DecodedCharSource.WHOLE_LIMIT;
        var valid = ("aé😀\n".repeat(limit / 8) + "z".repeat(limit % 8 + 1)).getBytes(StandardCharsets.UTF_8);
        var invalid = Arrays.copyOf(valid, limit + 10);
        invalid[limit + 5] = (byte) 0xFF;
        for (var bytes : List.of(valid, invalid, Arrays.copyOf(valid, limit), Arrays.copyOf(valid, limit + 1))) {
            var expected = decodedAsAStream(bytes, StandardCharsets.UTF_8);
            List<ByteSource> sources =
                    List.of(ByteSource.wrap(bytes), () -> ByteReader.of(new ByteArrayInputStream(bytes)));
            for (var source : sources) {
                assertThat(readWhole(source.asCharSource(StandardCharsets.UTF_8)))
                        .as("%s bytes from %s", bytes.length, source)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void readsTextWholeOnlyOnce() throws IOException {
        var source = new TrackedSource("text".getBytes(StandardCharsets.UTF_8));

        assertThat(source.asCharSource(StandardCharsets.UTF_8).read()).isEqualTo("text");
        assertThat(source.opened).isOne();
        assertThat(source.closed).isOne();
    }

    @Test
    void readsAnEmptyFile() throws IOException {
        var file = Files.createFile(directory.resolve("empty"));

        assertThat(ByteSource.of(file).read().isEmpty()).isTrue();
        assertThat(ByteSource.of(file).asCharSource().read()).isEmpty();
    }

    @Test
    void readsAFileOfSeveralBuffers() throws IOException {
        for (var size : new int[] {8191, 8192, 8193, 3 * 1024 * 1024 + 7}) {
            var bytes = bytes(size);
            var file = Files.write(directory.resolve("data-" + size), bytes);

            assertThat(ByteSource.of(file).read().toByteArray())
                    .as("%s bytes", size)
                    .isEqualTo(bytes);
        }
    }

    @Test
    void readsAFileThatReportsNoSize() throws IOException {
        // Files of /proc report a size of 0 whatever they hold.
        var file = Path.of("/proc/self/status");
        assumeTrue(Files.isReadable(file) && Files.size(file) == 0);

        assertThat(ByteSource.of(file).read().isEmpty()).isFalse();
    }

    @Test
    void failsAtOnceOnAFileTooLargeForAnArray() throws IOException {
        var file = directory.resolve("sparse");
        try (var out = new RandomAccessFile(file.toFile(), "rw")) {
            out.setLength(3L << 30);
        }

        for (var source : List.of(ByteSource.of(file), ByteSource.of(file, LinkOption.NOFOLLOW_LINKS))) {
            assertTimeoutPreemptively(
                    Duration.ofSeconds(10),
                    () -> assertThatExceptionOfType(OutOfMemoryError.class)
                            .isThrownBy(source::read)
                            .withMessage("Required array size too large: " + (3L << 30)));
        }
    }

    @Test
    void handlesFilesLargerThanAnArray() throws IOException {
        // A sparse file, past the 2 GiB that an array holds at most, whose only bytes are a marker.
        var file = directory.resolve("sparse");
        var size = (1L << 31) + 4096;
        var at = (1L << 31) + 100;
        var marker = new byte[] {1, 2, 3, 4};
        try (var out = new RandomAccessFile(file.toFile(), "rw")) {
            out.setLength(size);
            out.seek(at);
            out.write(marker);
        }
        var source = ByteSource.of(file);

        assertThat(source.sizeIfKnown()).hasValue(size);
        assertThat(source.size()).isEqualTo(size);
        assertThat(source.slice(at, 4).read().toByteArray()).isEqualTo(marker);
        assertThat(source.slice(at, 4).sizeIfKnown()).hasValue(4);
        assertThat(source.slice(size - 10, 100).sizeIfKnown()).hasValue(10);
        assertThat(source.slice(size - 10, 100).size()).isEqualTo(10);
        assertThat(ByteSource.concat(source, source).sizeIfKnown()).hasValue(2 * size);
        var concatenated = ByteSource.concat(ByteSource.wrap(marker), source, ByteSource.wrap(marker));
        assertThat(concatenated.slice(4 + at, 4).read().toByteArray()).isEqualTo(marker);
        assertThat(concatenated.slice(4 + size, 4).read().toByteArray()).isEqualTo(marker);
        assertThat(source.copyTo(OutputStream.nullOutputStream())).isEqualTo(size);
        try (var in = source.openReader()) {
            in.skip(at);
            assertThat(in.readInt()).isEqualTo(0x01020304);
            assertThat(in.readAll().size()).isEqualTo(size - at - 4);
        }
        try (var in = source.openReader().asInputStream()) {
            assertThat(in.skip(at)).isEqualTo(at);
            assertThat(in.readNBytes(4)).isEqualTo(marker);
        }
    }

    @Test
    void streamsWithoutReadingWhole() throws IOException {
        var text = "a\nb\nc".repeat(10_000);
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        var source = new ByteSource() {
            @Override
            public ByteReader openReader() {
                return ByteReader.of(new ByteArrayInputStream(bytes));
            }

            @Override
            public Bytes read() {
                throw new AssertionError("read whole");
            }
        };
        var chars = source.asCharSource(StandardCharsets.UTF_8);

        assertThat(source.copyTo(new ByteArrayOutputStream())).isEqualTo(bytes.length);
        assertThat(source.copyTo(ByteSink.of(directory.resolve("copy")))).isEqualTo(bytes.length);
        assertThat(source.contentEquals(ByteSource.wrap(bytes))).isTrue();
        assertThat(chars.readLines()).hasSize(20_001);
        try (var lines = chars.lines()) {
            assertThat(lines.count()).isEqualTo(20_001);
        }
        assertThat(chars.readFirstLine()).hasValue("a");
        assertThat(chars.copyTo(new StringBuilder())).isEqualTo(text.length());
        try (var in = chars.openReader()) {
            assertThat(in.readAll()).isEqualTo(text);
        }
    }

    @Test
    void streamsMoreThanAnArrayHolds() throws IOException {
        // Lines of 99 'a' and a '\n', past the 2 GiB that an array holds at most.
        var lineCount = (1L << 31) / 100 + 1;
        ByteSource source = () -> ByteReader.of(new GeneratedLines(lineCount));
        var counted = new long[1];

        assertThat(source.copyTo(OutputStream.nullOutputStream())).isEqualTo(lineCount * 100);
        source.asCharSource(StandardCharsets.UTF_8).forEachLine(line -> counted[0]++);
        assertThat(counted[0]).isEqualTo(lineCount);
    }

    @Test
    void opensAFileWithItsOptions() throws IOException {
        var file = Files.write(directory.resolve("data"), BYTES);
        var link = directory.resolve("link");
        try {
            Files.createSymbolicLink(link, file);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "no symbolic links: " + e);
        }

        assertThat(ByteSource.of(link).read().toByteArray()).containsExactly(1, 2, 3, 4, 5);
        assertThatIOException()
                .isThrownBy(() -> ByteSource.of(link, LinkOption.NOFOLLOW_LINKS).read());
    }

    @Test
    void readsBytes() throws IOException {
        var source = ByteSource.wrap(Bytes.of(BYTES).slice(1, 4));

        assertThat(source.read().toByteArray()).containsExactly(2, 3, 4);
        assertThat(source.sizeIfKnown()).hasValue(3);
    }

    @Test
    void opensAReader() throws IOException {
        try (var in = ByteSource.wrap(BYTES).openReader()) {
            assertThat(in.readShort()).isEqualTo((short) 0x0102);
            assertThat(in.readAll()).isEqualTo(Bytes.of(new byte[] {3, 4, 5}));
        }
    }

    @Test
    void opensAReaderOnASliceOfBytes() throws IOException {
        try (var in = ByteSource.wrap(Bytes.of(BYTES).slice(1, 4)).openReader()) {
            assertThat(in.readAll()).isEqualTo(Bytes.of(new byte[] {2, 3, 4}));
        }
    }

    // Counts the streams it opens and closes.
    private static final class TrackedSource implements ByteSource {

        private final byte[] bytes;
        private int opened;
        private int closed;

        TrackedSource(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public ByteReader openReader() {
            opened++;
            return ByteReader.of(new FilterInputStream(new ByteArrayInputStream(bytes)) {
                @Override
                public void close() throws IOException {
                    closed++;
                    super.close();
                }
            });
        }
    }

    // Generates lines of 99 'a' and a '\n', in constant memory.
    private static final class GeneratedLines extends InputStream {

        private static final byte[] LINE = ("a".repeat(99) + "\n").getBytes(StandardCharsets.US_ASCII);

        private final long size;
        private long position;

        GeneratedLines(long lineCount) {
            this.size = lineCount * LINE.length;
        }

        @Override
        public int read() {
            return position == size ? -1 : LINE[(int) (position++ % LINE.length)];
        }

        @Override
        public int read(byte[] b, int offset, int length) {
            if (length == 0) {
                return 0;
            }
            if (position == size) {
                return -1;
            }
            var count = (int) Math.min(length, size - position);
            for (var done = 0; done < count; ) {
                var start = (int) (position % LINE.length);
                var run = Math.min(count - done, LINE.length - start);
                System.arraycopy(LINE, start, b, offset + done, run);
                done += run;
                position += run;
            }
            return count;
        }
    }
}
