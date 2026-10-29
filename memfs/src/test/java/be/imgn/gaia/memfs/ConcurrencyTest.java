package be.imgn.gaia.memfs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Races between threads: each test repeats one, and waits a bounded time for its threads. */
class ConcurrencyTest {

    private static final int ROUNDS = 2_000;

    private final ExecutorService executor =
            Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());

    @AfterEach
    void shutDown() {
        executor.shutdownNow();
    }

    // Runs two tasks at once, as close together as the threads allow.
    private void race(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        for (Callable<?> task : List.of(first, second)) {
            tasks.add(executor.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        for (Future<?> task : tasks) {
            task.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void keepsTheContentOfAFileDeletedAsItIsOpened() throws Exception {
        try (FileSystem fileSystem = MemoryFileSystems.newFileSystem()) {
            Path file = fileSystem.getPath("file");
            for (int i = 0; i < ROUNDS; i++) {
                Files.writeString(file, "content");
                List<String> read = new ArrayList<>();
                race(
                        () -> {
                            try (SeekableByteChannel channel = Files.newByteChannel(file)) {
                                ByteBuffer buffer = ByteBuffer.allocate(16);
                                channel.read(buffer);
                                read.add(UTF_8.decode(buffer.flip()).toString());
                            } catch (NoSuchFileException e) {
                                read.add("content");
                            }
                            return null;
                        },
                        () -> Files.deleteIfExists(file));

                assertThat(read).containsExactly("content");
            }
        }
    }

    @Test
    void closesTheChannelsOpenedAsTheFileSystemCloses() throws Exception {
        for (int i = 0; i < ROUNDS; i++) {
            FileSystem fileSystem = MemoryFileSystems.newFileSystem();
            Path file = Files.writeString(fileSystem.getPath("file"), "content");
            List<SeekableByteChannel> opened = new ArrayList<>();
            race(
                    () -> {
                        try {
                            opened.add(Files.newByteChannel(file));
                        } catch (ClosedFileSystemException e) {
                            // Closed before: nothing to check.
                        }
                        return null;
                    },
                    () -> {
                        fileSystem.close();
                        return null;
                    });

            assertThat(opened).noneMatch(SeekableByteChannel::isOpen);
        }
    }
}
