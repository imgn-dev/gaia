package be.imgn.gaia.core.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import be.imgn.gaia.core.base.Charsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
}
