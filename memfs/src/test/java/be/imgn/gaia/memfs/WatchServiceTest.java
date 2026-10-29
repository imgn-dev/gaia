package be.imgn.gaia.memfs;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WatchServiceTest {

    private final FileSystem fileSystem = MemoryFileSystems.newFileSystem(Configuration.unix().toBuilder()
            .watchServicePollingInterval(Duration.ofMillis(10))
            .build());
    private final Path directory = fileSystem.getPath("/watched");

    @AfterEach
    void close() throws IOException {
        fileSystem.close();
    }

    private static List<String> events(WatchKey key) {
        List<String> events = new ArrayList<>();
        for (WatchEvent<?> event : key.pollEvents()) {
            events.add(event.kind().name() + " " + event.context() + " x" + event.count());
        }
        return events;
    }

    @Test
    void reportsCreatedModifiedAndDeletedEntries() throws Exception {
        Files.createDirectory(directory);
        Files.writeString(directory.resolve("kept"), "a");
        Files.writeString(directory.resolve("deleted"), "a");
        try (WatchService watcher = fileSystem.newWatchService()) {
            WatchKey registered = directory.register(watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);

            Files.createFile(directory.resolve("created"));
            Files.writeString(directory.resolve("kept"), "changed", StandardOpenOption.APPEND);
            Files.delete(directory.resolve("deleted"));
            WatchKey key = watcher.poll(5, TimeUnit.SECONDS);

            assertThat(key).isSameAs(registered);
            assertThat(key.watchable()).isEqualTo(directory);
            Thread.sleep(50);
            assertThat(events(key))
                    .containsExactlyInAnyOrder(
                            "ENTRY_CREATE created x1", "ENTRY_MODIFY kept x1", "ENTRY_DELETE deleted x1");
            assertThat(key.reset()).isTrue();
            assertThat(watcher.poll()).isNull();
        }
    }

    @Test
    void reportsModificationsWhenTheClockStandsStill() throws Exception {
        try (FileSystem frozen = MemoryFileSystems.newFileSystem(Configuration.unix().toBuilder()
                .watchServicePollingInterval(Duration.ofMillis(10))
                .clock(InstantSource.fixed(Instant.parse("2026-01-01T00:00:00Z")))
                .build())) {
            Path watched = frozen.getPath("/watched");
            Files.createDirectory(watched);
            Files.writeString(watched.resolve("kept"), "a");
            try (WatchService watcher = frozen.newWatchService()) {
                watched.register(watcher, ENTRY_MODIFY);

                Files.writeString(watched.resolve("kept"), "changed", StandardOpenOption.APPEND);
                WatchKey key = watcher.poll(5, TimeUnit.SECONDS);

                assertThat(key).isNotNull();
                assertThat(events(key)).containsExactly("ENTRY_MODIFY kept x1");
            }
        }
    }

    @Test
    void reportsOnlyTheKindsItWatches() throws Exception {
        Files.createDirectory(directory);
        try (WatchService watcher = fileSystem.newWatchService()) {
            directory.register(watcher, ENTRY_DELETE);

            // Each change lasts longer than a polling interval, so that the service sees it.
            Files.createFile(directory.resolve("created"));
            Thread.sleep(50);
            Files.delete(directory.resolve("created"));
            WatchKey key = watcher.poll(5, TimeUnit.SECONDS);

            assertThat(events(key)).containsExactly("ENTRY_DELETE created x1");
        }
    }

    @Test
    void countsRepeatedEvents() throws Exception {
        Files.createDirectory(directory);
        Path file = Files.createFile(directory.resolve("file"));
        try (WatchService watcher = fileSystem.newWatchService()) {
            directory.register(watcher, ENTRY_MODIFY);

            for (int i = 0; i < 3; i++) {
                Files.writeString(file, "change " + i, StandardOpenOption.APPEND);
                Thread.sleep(50);
            }
            WatchKey key = watcher.poll(5, TimeUnit.SECONDS);

            assertThat(events(key)).containsExactly("ENTRY_MODIFY file x3");
        }
    }

    @Test
    void overflowsPastAMaximumOfEvents() throws Exception {
        Files.createDirectory(directory);
        try (WatchService watcher = fileSystem.newWatchService()) {
            directory.register(watcher, ENTRY_CREATE);

            for (int i = 0; i < 600; i++) {
                Files.createFile(directory.resolve("file" + i));
            }
            WatchKey key = watcher.poll(5, TimeUnit.SECONDS);
            Thread.sleep(50);

            assertThat(events(key)).containsExactly("OVERFLOW null x88");
        }
    }

    @Test
    void signalsAgainAfterAResetWithPendingEvents() throws Exception {
        Files.createDirectory(directory);
        try (WatchService watcher = fileSystem.newWatchService()) {
            directory.register(watcher, ENTRY_CREATE);
            Files.createFile(directory.resolve("first"));
            WatchKey key = watcher.poll(5, TimeUnit.SECONDS);

            Files.createFile(directory.resolve("second"));
            Thread.sleep(100);
            key.reset();

            assertThat(watcher.poll(5, TimeUnit.SECONDS)).isSameAs(key);
            assertThat(events(key)).containsExactly("ENTRY_CREATE first x1", "ENTRY_CREATE second x1");
        }
    }

    @Test
    void cancelsKeys() throws Exception {
        Files.createDirectory(directory);
        try (WatchService watcher = fileSystem.newWatchService()) {
            WatchKey key = directory.register(watcher, ENTRY_CREATE);

            key.cancel();

            assertThat(key.isValid()).isFalse();
            assertThat(key.reset()).isFalse();
            assertThat(directory.register(watcher, ENTRY_CREATE)).isNotSameAs(key);
        }
    }

    @Test
    void cancelsAndSignalsTheKeyOfADeletedDirectory() throws Exception {
        Files.createDirectory(directory);
        try (WatchService watcher = fileSystem.newWatchService()) {
            WatchKey registered = directory.register(watcher, ENTRY_CREATE);

            Files.delete(directory);
            WatchKey key = watcher.poll(5, TimeUnit.SECONDS);

            assertThat(key).isSameAs(registered);
            assertThat(key.isValid()).isFalse();
            assertThat(key.reset()).isFalse();
            Files.createDirectory(directory);
            assertThat(directory.register(watcher, ENTRY_CREATE)).isNotSameAs(key);
        }
    }

    @Test
    void refusesWhatItCannotWatch() throws Exception {
        Files.createDirectory(directory);
        Path file = Files.createFile(directory.resolve("file"));
        try (WatchService watcher = fileSystem.newWatchService();
                WatchService foreign = FileSystems.getDefault().newWatchService()) {
            assertThatExceptionOfType(NotDirectoryException.class)
                    .isThrownBy(() -> file.register(watcher, ENTRY_CREATE));
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> fileSystem.getPath("/missing").register(watcher, ENTRY_CREATE));
            assertThatExceptionOfType(ProviderMismatchException.class)
                    .isThrownBy(() -> directory.register(foreign, ENTRY_CREATE));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> directory.register(watcher, new WatchEvent.Kind<?>[] {customKind()}));
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> directory.register(
                            watcher, new WatchEvent.Kind<?>[] {ENTRY_CREATE}, new WatchEvent.Modifier() {
                                @Override
                                public String name() {
                                    return "SENSITIVE";
                                }
                            }));
        }
    }

    @Test
    void refusesEverythingOnceClosed() throws Exception {
        Files.createDirectory(directory);
        WatchService watcher = fileSystem.newWatchService();
        WatchKey key = directory.register(watcher, StandardWatchEventKinds.OVERFLOW);

        watcher.close();
        watcher.close();

        assertThat(key.isValid()).isFalse();
        assertThatExceptionOfType(ClosedWatchServiceException.class).isThrownBy(watcher::poll);
        assertThatExceptionOfType(ClosedWatchServiceException.class).isThrownBy(watcher::take);
        assertThatExceptionOfType(ClosedWatchServiceException.class)
                .isThrownBy(() -> watcher.poll(1, TimeUnit.MILLISECONDS));
        assertThatExceptionOfType(ClosedWatchServiceException.class)
                .isThrownBy(() -> directory.register(watcher, ENTRY_CREATE));
    }

    @Test
    void wakesUpATakeWhenClosed() throws Exception {
        WatchService watcher = fileSystem.newWatchService();
        Thread closer = new Thread(() -> {
            try {
                Thread.sleep(50);
                watcher.close();
            } catch (InterruptedException | IOException e) {
                Thread.currentThread().interrupt();
            }
        });
        closer.start();

        assertThatExceptionOfType(ClosedWatchServiceException.class).isThrownBy(watcher::take);
        closer.join();
    }

    @Test
    void wakesUpEveryTakeWhenClosed() throws Exception {
        WatchService watcher = fileSystem.newWatchService();
        ExecutorService executor =
                Executors.newFixedThreadPool(3, Thread.ofPlatform().daemon().factory());
        try {
            CountDownLatch taking = new CountDownLatch(3);
            List<Future<?>> takes = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                takes.add(executor.submit(() -> {
                    taking.countDown();
                    return watcher.take();
                }));
            }
            taking.await();
            Thread.sleep(50);

            watcher.close();

            for (Future<?> take : takes) {
                assertThatExceptionOfType(ExecutionException.class)
                        .isThrownBy(() -> take.get(5, TimeUnit.SECONDS))
                        .withCauseInstanceOf(ClosedWatchServiceException.class);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void isClosedWithTheFileSystem() throws Exception {
        WatchService watcher = fileSystem.newWatchService();

        fileSystem.close();

        assertThatExceptionOfType(ClosedWatchServiceException.class).isThrownBy(watcher::poll);
    }

    private static WatchEvent.Kind<Object> customKind() {
        return new WatchEvent.Kind<>() {
            @Override
            public String name() {
                return "CUSTOM";
            }

            @Override
            public Class<Object> type() {
                return Object.class;
            }
        };
    }
}
