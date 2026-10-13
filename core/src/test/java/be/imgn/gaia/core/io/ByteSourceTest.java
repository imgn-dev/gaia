package be.imgn.gaia.core.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
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

        assertThat(source.read()).containsExactly(1, 2, 3, 4, 5);
        assertThat(source.sizeIfKnown()).hasValue(5);
        assertThat(source.isEmpty()).isFalse();
    }

    @Test
    void readsTheCurrentContentOfAWrappedArray() throws IOException {
        var bytes = new byte[] {1, 2};
        var source = ByteSource.wrap(bytes);

        bytes[0] = 9;

        assertThat(source.read()).containsExactly(9, 2);
    }

    @Test
    void readsNothingFromTheEmptySource() throws IOException {
        var source = ByteSource.empty();

        assertThat(source.read()).isEmpty();
        assertThat(source.size()).isZero();
        assertThat(source.isEmpty()).isTrue();
    }

    @Test
    void readsALambda() throws IOException {
        ByteSource source = () -> new ByteArrayInputStream(BYTES);

        assertThat(source.read()).containsExactly(1, 2, 3, 4, 5);
        assertThat(source.sizeIfKnown()).isEmpty();
        assertThat(source.size()).isEqualTo(5);
        assertThat(source.isEmpty()).isFalse();
        assertThat(((ByteSource) () -> new ByteArrayInputStream(new byte[0])).isEmpty())
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

        assertThat(source.read()).containsExactly(1, 2, 3, 4, 5);
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

        assertThat(ByteSource.of(file.toUri().toURL()).read()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void readsAUri() throws IOException {
        var file = Files.write(directory.resolve("data"), BYTES);

        assertThat(ByteSource.of(file.toUri()).read()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void rejectsAUriThatIsNotAUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> ByteSource.of(URI.create("relative/path")));
        assertThatIllegalArgumentException().isThrownBy(() -> ByteSource.of(URI.create("unknown:path")));
    }

    @Test
    void opensABufferedStream() throws IOException {
        ByteSource source = () -> new ByteArrayInputStream(BYTES);

        try (var in = source.openBufferedStream()) {
            assertThat(in).isInstanceOf(BufferedInputStream.class);
            assertThat(in.readAllBytes()).containsExactly(1, 2, 3, 4, 5);
        }
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

        assertThat(ByteSource.wrap(BYTES).copyTo(() -> out)).isEqualTo(5);
        assertThat(out.toByteArray()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void comparesContent() throws IOException {
        var large = bytes(20_000);
        var changed = large.clone();
        changed[19_999] = 0;
        ByteSource unknownSize = () -> new ByteArrayInputStream(large);

        assertThat(ByteSource.wrap(large).contentEquals(unknownSize)).isTrue();
        assertThat(ByteSource.wrap(changed).contentEquals(unknownSize)).isFalse();
        assertThat(ByteSource.wrap(Arrays.copyOf(large, 19_999)).contentEquals(unknownSize))
                .isFalse();
        assertThat(ByteSource.empty().contentEquals(() -> new ByteArrayInputStream(new byte[0])))
                .isTrue();
    }

    @Test
    void comparesKnownSizesWithoutReading() throws IOException {
        var unreadable = new ByteSource() {
            @Override
            public InputStream openStream() throws IOException {
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

        assertThat(source.read()).containsExactly(2, 3, 4);
        assertThat(source.sizeIfKnown()).hasValue(3);
    }

    @Test
    void readsASliceThatGoesPastTheEnd() throws IOException {
        var source = ByteSource.wrap(BYTES).slice(3, 10);

        assertThat(source.read()).containsExactly(4, 5);
        assertThat(source.sizeIfKnown()).hasValue(2);
    }

    @Test
    void readsNothingFromASliceThatStartsPastTheEnd() throws IOException {
        var source = ByteSource.wrap(BYTES).slice(10, 2);

        assertThat(source.read()).isEmpty();
        assertThat(source.sizeIfKnown()).hasValue(0);
        assertThat(source.isEmpty()).isTrue();
    }

    @Test
    void readsASliceOfALargeStreamByteByByte() throws IOException {
        var large = bytes(20_000);
        ByteSource source = () -> new ByteArrayInputStream(large);
        var slice = source.slice(10_000, 3);

        assertThat(slice.sizeIfKnown()).isEmpty();
        try (var in = slice.openStream()) {
            assertThat(in.read()).isEqualTo(Byte.toUnsignedInt(large[10_000]));
            assertThat(in.read()).isEqualTo(Byte.toUnsignedInt(large[10_001]));
            assertThat(in.read()).isEqualTo(Byte.toUnsignedInt(large[10_002]));
            assertThat(in.read()).isEqualTo(-1);
        }
    }

    @Test
    void readsASliceOfASlice() throws IOException {
        assertThat(ByteSource.wrap(BYTES).slice(1, 3).slice(1, 5).read()).containsExactly(3, 4);
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

        assertThat(source.read()).containsExactly(1, 2, 1, 2, 3, 4, 5);
        assertThat(source.sizeIfKnown()).hasValue(7);
        assertThat(source.isEmpty()).isFalse();
    }

    @Test
    void readsConcatenatedSourcesByteByByte() throws IOException {
        var source = ByteSource.concat(ByteSource.wrap(new byte[] {1}), ByteSource.empty(), ByteSource.wrap(BYTES));

        var read = new ArrayList<Integer>();
        try (var in = source.openStream()) {
            for (var b = in.read(); b != -1; b = in.read()) {
                read.add(b);
            }
        }

        assertThat(read).containsExactly(1, 1, 2, 3, 4, 5);
    }

    @Test
    void opensEachConcatenatedSourceWhenItsTurnComes() throws IOException {
        var first = new TrackedSource(new byte[] {1});
        var second = new TrackedSource(new byte[] {2});

        try (var in = ByteSource.concat(first, second).openStream()) {
            assertThat(second.opened).isZero();
            in.read();
            in.read();

            assertThat(first.closed).isEqualTo(1);
            assertThat(second.opened).isEqualTo(1);
        }
        assertThat(second.closed).isEqualTo(1);
    }

    @Test
    void doesNotKnowTheSizeOfConcatenatedSourcesIfOneIsUnknown() throws IOException {
        var source = ByteSource.concat(ByteSource.wrap(BYTES), () -> new ByteArrayInputStream(BYTES));

        assertThat(source.sizeIfKnown()).isEmpty();
        assertThat(source.size()).isEqualTo(10);
    }

    @Test
    void readsNothingFromNoConcatenatedSources() throws IOException {
        var source = ByteSource.concat();

        assertThat(source.read()).isEmpty();
        assertThat(source.isEmpty()).isTrue();
        assertThat(ByteSource.concat(ByteSource.empty(), ByteSource.empty()).isEmpty())
                .isTrue();
    }

    @Test
    void copiesTheIterableOfConcatenatedSources() throws IOException {
        var sources = new ArrayList<ByteSource>(List.of(ByteSource.wrap(BYTES)));
        var source = ByteSource.concat(sources);

        sources.add(ByteSource.wrap(BYTES));

        assertThat(source.read()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void decodesCharacters() throws IOException {
        var source = ByteSource.wrap("café ✓".getBytes(StandardCharsets.UTF_8));

        assertThat(source.asCharSource(StandardCharsets.UTF_8).read()).isEqualTo("café ✓");
    }

    @Test
    void failsOnBytesThatAreNotValidInTheCharset() {
        var source = ByteSource.wrap(new byte[] {'a', (byte) 0xFF, 'b'}).asCharSource(StandardCharsets.UTF_8);

        assertThatExceptionOfType(MalformedInputException.class).isThrownBy(source::read);
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
        public InputStream openStream() {
            opened++;
            return new FilterInputStream(new ByteArrayInputStream(bytes)) {
                @Override
                public void close() throws IOException {
                    closed++;
                    super.close();
                }
            };
        }
    }
}
