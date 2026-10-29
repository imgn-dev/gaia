package be.imgn.gaia.memfs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChannelTest {

    private final FileSystem fileSystem = MemoryFileSystems.newFileSystem(
            Configuration.unix().toBuilder().blockSize(4).build());
    private final Path file = fileSystem.getPath("file");

    @AfterEach
    void close() throws IOException {
        fileSystem.close();
    }

    private static String string(ByteBuffer buffer) {
        return UTF_8.decode(buffer.duplicate().flip()).toString();
    }

    @Nested
    class Seekable {

        @Test
        void readsAndWritesAtThePosition() throws IOException {
            try (SeekableByteChannel channel = Files.newByteChannel(
                    file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                assertThat(channel.write(ByteBuffer.wrap("Hello, world".getBytes(UTF_8))))
                        .isEqualTo(12);
                assertThat(channel.position()).isEqualTo(12);
                assertThat(channel.size()).isEqualTo(12);

                channel.position(7);
                ByteBuffer buffer = ByteBuffer.allocate(20);
                assertThat(channel.read(buffer)).isEqualTo(5);
                assertThat(string(buffer)).isEqualTo("world");
                assertThat(channel.read(ByteBuffer.allocate(1))).isEqualTo(-1);

                channel.position(20);
                channel.write(ByteBuffer.wrap(new byte[] {'!'}));
                assertThat(channel.size()).isEqualTo(21);
            }
            byte[] content = Files.readAllBytes(file);
            assertThat(content[15]).isZero();
            assertThat(content[20]).isEqualTo((byte) '!');
        }

        @Test
        void appendsAtTheEnd() throws IOException {
            Files.writeString(file, "abc");

            try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.APPEND)) {
                channel.position(0);
                channel.write(ByteBuffer.wrap("de".getBytes(UTF_8)));

                assertThat(channel.position()).isEqualTo(5);
            }

            assertThat(Files.readString(file)).isEqualTo("abcde");
        }

        @Test
        void truncates() throws IOException {
            Files.writeString(file, "abcdefgh");

            try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.WRITE)) {
                channel.position(6);
                channel.truncate(3);
                assertThat(channel.position()).isEqualTo(3);
                channel.truncate(10);
                assertThat(channel.size()).isEqualTo(3);
            }

            assertThat(Files.readString(file)).isEqualTo("abc");
        }

        @Test
        void refusesWhatTheChannelWasNotOpenedFor() throws IOException {
            Files.writeString(file, "abc");

            try (SeekableByteChannel reading = Files.newByteChannel(file);
                    SeekableByteChannel writing = Files.newByteChannel(file, StandardOpenOption.WRITE)) {
                assertThatExceptionOfType(NonWritableChannelException.class)
                        .isThrownBy(() -> reading.write(ByteBuffer.allocate(1)));
                assertThatExceptionOfType(NonWritableChannelException.class).isThrownBy(() -> reading.truncate(0));
                assertThatExceptionOfType(NonReadableChannelException.class)
                        .isThrownBy(() -> writing.read(ByteBuffer.allocate(1)));
                assertThatIllegalArgumentException().isThrownBy(() -> reading.position(-1));
                assertThatIllegalArgumentException().isThrownBy(() -> writing.truncate(-1));
            }
        }

        @Test
        void refusesEverythingOnceClosed() throws IOException {
            SeekableByteChannel channel =
                    Files.newByteChannel(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            channel.close();
            channel.close();

            assertThat(channel.isOpen()).isFalse();
            assertThatExceptionOfType(ClosedChannelException.class)
                    .isThrownBy(() -> channel.write(ByteBuffer.allocate(1)));
            assertThatExceptionOfType(ClosedChannelException.class).isThrownBy(channel::position);
            assertThatExceptionOfType(ClosedChannelException.class).isThrownBy(channel::size);
            assertThatExceptionOfType(ClosedChannelException.class).isThrownBy(() -> channel.position(0));
        }

        @Test
        void streams() throws IOException {
            try (var out = Files.newOutputStream(file)) {
                out.write("streamed".getBytes(UTF_8));
            }
            try (var in = Files.newInputStream(file);
                    var reader = Files.newBufferedReader(file)) {
                assertThat(in.readAllBytes()).asString(UTF_8).isEqualTo("streamed");
                assertThat(reader.readLine()).isEqualTo("streamed");
            }
            try (var writer = Files.newBufferedWriter(file, StandardOpenOption.APPEND)) {
                writer.write(" more");
            }
            assertThat(Files.readString(file)).isEqualTo("streamed more");
        }
    }

    @Nested
    class FileChannels {

        @Test
        void readsAndWritesAtAbsolutePositions() throws IOException {
            try (FileChannel channel = FileChannel.open(
                    file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap("0123456789".getBytes(UTF_8)), 0);
                channel.write(ByteBuffer.wrap("ab".getBytes(UTF_8)), 4);
                ByteBuffer buffer = ByteBuffer.allocate(3);

                assertThat(channel.read(buffer, 3)).isEqualTo(3);
                assertThat(string(buffer)).isEqualTo("3ab");
                assertThat(channel.position()).isZero();
                assertThat(channel.read(ByteBuffer.allocate(1), 100)).isEqualTo(-1);
                assertThatIllegalArgumentException().isThrownBy(() -> channel.read(ByteBuffer.allocate(1), -1));
                assertThatIllegalArgumentException().isThrownBy(() -> channel.write(ByteBuffer.allocate(1), -1));
                channel.force(true);
            }
        }

        @Test
        void writesNothingForAnEmptyBufferPastTheEnd() throws IOException {
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                assertThat(channel.write(ByteBuffer.allocate(0), 1000)).isZero();

                assertThat(channel.size()).isZero();
            }
        }

        @Test
        void refusesToWritePastTheLargestSize() throws IOException {
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                assertThatExceptionOfType(FileSystemException.class)
                        .isThrownBy(() -> channel.write(ByteBuffer.allocate(10), Long.MAX_VALUE - 5));
                assertThat(channel.size()).isZero();
            }
        }

        @Test
        void refusesToReleaseALockOfAClosedChannel() throws IOException {
            FileLock lock;
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                lock = channel.lock();
            }

            assertThat(lock.isValid()).isFalse();
            assertThatExceptionOfType(ClosedChannelException.class).isThrownBy(lock::release);
        }

        @Test
        void transfersWhatATargetWithNoMoreRoomTakes() throws IOException {
            Files.writeString(file, "transferred content");
            var taken = new StringBuilder();
            WritableByteChannel full = new WritableByteChannel() {
                @Override
                public int write(ByteBuffer src) {
                    if (taken.length() >= 3) {
                        return 0;
                    }
                    taken.append((char) src.get());
                    return 1;
                }

                @Override
                public boolean isOpen() {
                    return true;
                }

                @Override
                public void close() {}
            };

            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
                long transferred =
                        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> channel.transferTo(0, 100, full));

                assertThat(transferred).isEqualTo(3);
                assertThat(taken).hasToString("tra");
            }
        }

        @Test
        void scattersAndGathers() throws IOException {
            try (FileChannel channel = FileChannel.open(
                    file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                ByteBuffer[] sources = {ByteBuffer.wrap("abc".getBytes(UTF_8)), ByteBuffer.wrap("defg".getBytes(UTF_8))
                };
                assertThat(channel.write(sources)).isEqualTo(7);

                channel.position(0);
                ByteBuffer first = ByteBuffer.allocate(2);
                ByteBuffer second = ByteBuffer.allocate(10);
                assertThat(channel.read(new ByteBuffer[] {first, second})).isEqualTo(7);
                assertThat(string(first) + string(second)).isEqualTo("abcdefg");
                assertThat(channel.read(new ByteBuffer[] {ByteBuffer.allocate(1)}))
                        .isEqualTo(-1);
                assertThatExceptionOfType(IndexOutOfBoundsException.class)
                        .isThrownBy(() -> channel.read(new ByteBuffer[1], 1, 1));
            }
        }

        @Test
        void transfers() throws IOException {
            Files.writeString(file, "transferred content");
            Path copy = fileSystem.getPath("copy");

            try (FileChannel source = FileChannel.open(file);
                    FileChannel target = FileChannel.open(copy, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                assertThat(source.transferTo(12, 100, target)).isEqualTo(7);
                assertThat(target.transferFrom(
                                Channels.newChannel(new ByteArrayInputStream("!".getBytes(UTF_8))), 7, 10))
                        .isEqualTo(1);
                assertThat(target.transferFrom(source, 100, 10)).isZero();
                assertThatIllegalArgumentException().isThrownBy(() -> source.transferTo(-1, 1, target));
            }

            assertThat(Files.readString(copy)).isEqualTo("content!");
        }

        @Test
        void locksRegionsForTheWholeJvm() throws IOException {
            Files.writeString(file, "content");

            try (FileChannel first = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
                    FileChannel second = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                FileLock lock = first.lock(0, 4, false);

                assertThat(lock.isValid()).isTrue();
                assertThat(lock.channel()).isSameAs(first);
                assertThatExceptionOfType(OverlappingFileLockException.class)
                        .isThrownBy(() -> second.tryLock(2, 4, true));
                FileLock other = second.tryLock(4, 4, false);
                lock.release();
                assertThat(lock.isValid()).isFalse();
                assertThat(second.tryLock(0, 4, true)).isNotNull();
                assertThat(other.isValid()).isTrue();
            }
        }

        @Test
        void releasesLocksWhenTheChannelCloses() throws IOException {
            Files.writeString(file, "content");
            FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE);
            FileLock lock = channel.lock();

            channel.close();

            assertThat(lock.isValid()).isFalse();
            try (FileChannel again = FileChannel.open(file, StandardOpenOption.WRITE)) {
                assertThat(again.tryLock()).isNotNull();
            }
        }

        @Test
        void locksWhileOtherChannelsCloseOnOtherThreads() throws Exception {
            // A deadlock would also block closing its channels, so they get a file system of their own.
            FileSystem isolated = MemoryFileSystems.newFileSystem();
            Path shared = Files.createFile(isolated.getPath("shared"));
            ExecutorService executor =
                    Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
            try {
                Future<?> closing = executor.submit(() -> {
                    for (int i = 0; i < 5_000; i++) {
                        try (FileChannel channel = FileChannel.open(shared, StandardOpenOption.WRITE)) {
                            for (int position = 0; position < 10; position++) {
                                channel.lock(position, 1, false);
                            }
                        }
                    }
                    return null;
                });
                Future<?> locking = executor.submit(() -> {
                    try (FileChannel channel = FileChannel.open(shared, StandardOpenOption.WRITE)) {
                        for (int i = 0; i < 50_000; i++) {
                            channel.lock(100, 1, false).release();
                        }
                    }
                    return null;
                });

                closing.get(10, TimeUnit.SECONDS);
                locking.get(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdown();
            }
            isolated.close();
        }

        @Test
        void refusesLocksTheChannelCannotTake() throws IOException {
            Files.writeString(file, "content");

            try (FileChannel reading = FileChannel.open(file);
                    FileChannel writing = FileChannel.open(file, StandardOpenOption.WRITE)) {
                assertThatExceptionOfType(NonWritableChannelException.class)
                        .isThrownBy(() -> reading.lock(0, 1, false));
                assertThatExceptionOfType(NonReadableChannelException.class).isThrownBy(() -> writing.lock(0, 1, true));
                assertThatIllegalArgumentException().isThrownBy(() -> writing.lock(-1, 1, false));
            }
        }

        @Test
        void cannotBeMapped() throws IOException {
            Files.writeString(file, "content");

            try (FileChannel channel = FileChannel.open(file)) {
                assertThatExceptionOfType(UnsupportedOperationException.class)
                        .isThrownBy(() -> channel.map(FileChannel.MapMode.READ_ONLY, 0, 1));
            }
        }

        @Test
        void areClosedWithTheFileSystem() throws IOException {
            Files.writeString(file, "content");
            FileChannel channel = FileChannel.open(file);

            fileSystem.close();

            assertThat(channel.isOpen()).isFalse();
        }
    }

    @Nested
    class Asynchronous {

        @Test
        void readsAndWritesWithFutures() throws Exception {
            try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(
                    file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                assertThat(channel.write(ByteBuffer.wrap("async".getBytes(UTF_8)), 0)
                                .get(5, TimeUnit.SECONDS))
                        .isEqualTo(5);
                ByteBuffer buffer = ByteBuffer.allocate(10);
                assertThat(channel.read(buffer, 1).get(5, TimeUnit.SECONDS)).isEqualTo(4);
                assertThat(string(buffer)).isEqualTo("sync");
                assertThat(channel.size()).isEqualTo(5);
                channel.truncate(2).force(false);
                assertThat(channel.size()).isEqualTo(2);
            }
        }

        @Test
        void completesHandlers() throws Exception {
            var executor = Executors.newSingleThreadExecutor();
            try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(
                    file,
                    Set.of(StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE),
                    executor)) {
                CompletableFuture<Integer> written = new CompletableFuture<>();
                channel.write(ByteBuffer.wrap("abc".getBytes(UTF_8)), 0, "attachment", handler(written));
                assertThat(written.get(5, TimeUnit.SECONDS)).isEqualTo(3);

                CompletableFuture<Integer> read = new CompletableFuture<>();
                channel.read(ByteBuffer.allocate(5), 0, "attachment", handler(read));
                assertThat(read.get(5, TimeUnit.SECONDS)).isEqualTo(3);

                CompletableFuture<FileLock> locked = new CompletableFuture<>();
                channel.lock(0, 1, false, "attachment", handler(locked));
                FileLock lock = locked.get(5, TimeUnit.SECONDS);
                assertThat(lock.acquiredBy()).isSameAs(channel);

                CompletableFuture<FileLock> overlapping = new CompletableFuture<>();
                channel.lock(0, 1, false, "attachment", handler(overlapping));
                assertThatExceptionOfType(ExecutionException.class)
                        .isThrownBy(() -> overlapping.get(5, TimeUnit.SECONDS))
                        .withCauseInstanceOf(OverlappingFileLockException.class);
            } finally {
                executor.shutdown();
            }
        }

        @Test
        void locks() throws Exception {
            try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(
                    file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                FileLock lock = channel.lock().get(5, TimeUnit.SECONDS);

                assertThatExceptionOfType(OverlappingFileLockException.class).isThrownBy(channel::tryLock);
                lock.release();
                assertThat(channel.tryLock(0, 1, true)).isNotNull();
                assertThatIllegalArgumentException().isThrownBy(() -> channel.tryLock(-1, 1, true));
            }
        }

        @Test
        void refusesWhatTheChannelWasNotOpenedFor() throws IOException {
            Files.writeString(file, "content");

            try (AsynchronousFileChannel channel = AsynchronousFileChannel.open(file)) {
                assertThatExceptionOfType(NonWritableChannelException.class)
                        .isThrownBy(() -> channel.tryLock(0, 1, false));
                assertThatIllegalArgumentException().isThrownBy(() -> channel.read(ByteBuffer.allocate(1), -1));
                try (AsynchronousFileChannel writing = AsynchronousFileChannel.open(file, StandardOpenOption.WRITE)) {
                    assertThatExceptionOfType(NonReadableChannelException.class)
                            .isThrownBy(() -> writing.tryLock(0, 1, true));
                }
                assertThatIllegalArgumentException()
                        .isThrownBy(() -> channel.read(ByteBuffer.allocate(1).asReadOnlyBuffer(), 0));
                assertThatIllegalArgumentException().isThrownBy(() -> channel.write(ByteBuffer.allocate(1), -1));
                assertThatExceptionOfType(ExecutionException.class)
                        .isThrownBy(
                                () -> channel.write(ByteBuffer.allocate(1), 0).get(5, TimeUnit.SECONDS))
                        .withCauseInstanceOf(NonWritableChannelException.class);
            }
            AsynchronousFileChannel closed = AsynchronousFileChannel.open(file);
            closed.close();
            assertThat(closed.isOpen()).isFalse();
        }

        private <T> CompletionHandler<T, String> handler(CompletableFuture<T> future) {
            return new CompletionHandler<>() {
                @Override
                public void completed(T result, String attachment) {
                    future.complete(result);
                }

                @Override
                public void failed(Throwable failure, String attachment) {
                    future.completeExceptionally(failure);
                }
            };
        }
    }

    @Nested
    class Opening {

        @Test
        void needsTheFileUnlessCreatingIt() {
            assertThatExceptionOfType(NoSuchFileException.class).isThrownBy(() -> FileChannel.open(file));
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> Files.newByteChannel(file, StandardOpenOption.CREATE));
        }

        @Test
        void refusesFileChannelsWithoutTheFeature() throws IOException {
            try (FileSystem plain = MemoryFileSystems.newFileSystem(
                    Configuration.unix().toBuilder().features().build())) {
                Path path = Files.writeString(plain.getPath("file"), "content");

                assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> FileChannel.open(path));
                assertThatExceptionOfType(UnsupportedOperationException.class)
                        .isThrownBy(() -> AsynchronousFileChannel.open(path));
                assertThat(Files.readString(path)).isEqualTo("content");
            }
        }

        @Test
        void refusesDirectories() {
            assertThatExceptionOfType(FileSystemException.class)
                    .isThrownBy(() -> FileChannel.open(fileSystem.getPath("/work"), StandardOpenOption.WRITE));
        }
    }
}
