package be.imgn.gaia.memfs.internal;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * A watch service that compares the entries of the directories it watches at the polling interval of the configuration:
 * an entry that appears, disappears or is modified is an event.
 */
final class PollingWatchService implements WatchService {

    private static final Set<WatchEvent.Kind<?>> KINDS = Set.of(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW);

    private final MemoryFileSystem fileSystem;
    private final BlockingQueue<Key> signalled = new LinkedBlockingQueue<>();
    private final Map<Directory, Key> keys = new HashMap<>();
    private final ScheduledExecutorService poller;
    private final ScheduledFuture<?> scanning;
    private volatile boolean open = true;

    PollingWatchService(MemoryFileSystem fileSystem) {
        this.fileSystem = fileSystem;
        this.poller = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "memfs-watch-" + fileSystem.name());
            thread.setDaemon(true);
            return thread;
        });
        long interval = fileSystem.configuration().watchServicePollingInterval().toNanos();
        this.scanning = poller.scheduleWithFixedDelay(this::scan, interval, interval, TimeUnit.NANOSECONDS);
    }

    MemoryFileSystem fileSystem() {
        return fileSystem;
    }

    WatchKey register(MemoryPath path, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
            throws IOException {
        requireNonNull(events, "events");
        requireNonNull(modifiers, "modifiers");
        if (modifiers.length > 0) {
            throw new UnsupportedOperationException("Unsupported modifier: " + modifiers[0]);
        }
        Set<WatchEvent.Kind<?>> kinds = new HashSet<>();
        for (WatchEvent.Kind<?> kind : events) {
            if (!KINDS.contains(kind)) {
                throw new UnsupportedOperationException("Unsupported event kind: " + kind);
            }
            kinds.add(kind);
        }
        checkOpen();
        if (!(fileSystem.operations().node(null, path, true) instanceof Directory directory)) {
            throw new NotDirectoryException(path.toString());
        }
        synchronized (keys) {
            Key key = keys.get(directory);
            if (key == null || !key.isValid()) {
                key = new Key(path, directory);
                keys.put(directory, key);
            }
            key.watch(kinds, snapshot(directory));
            return key;
        }
    }

    private Map<String, Entry> snapshot(Directory directory) {
        Map<String, Entry> entries = new HashMap<>();
        for (Directory.Entry entry : directory.entries()) {
            Node node = entry.node();
            entries.put(entry.name(), new Entry(node, node.lastModifiedTime()));
        }
        return entries;
    }

    private void scan() {
        List<Key> watched;
        synchronized (keys) {
            watched = new ArrayList<>(keys.values());
        }
        for (Key key : watched) {
            if (key.isValid()) {
                key.compare(snapshot(key.directory));
            }
        }
    }

    @Override
    public @Nullable WatchKey poll() {
        checkOpen();
        return check(signalled.poll());
    }

    @Override
    public @Nullable WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
        checkOpen();
        return check(signalled.poll(timeout, requireNonNull(unit, "unit")));
    }

    @Override
    public WatchKey take() throws InterruptedException {
        checkOpen();
        return requireNonNull(check(signalled.take()));
    }

    private @Nullable Key check(@Nullable Key key) {
        if (!open) {
            throw new ClosedWatchServiceException();
        }
        return key;
    }

    private void checkOpen() {
        if (!open) {
            throw new ClosedWatchServiceException();
        }
    }

    @Override
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        scanning.cancel(false);
        poller.shutdownNow();
        synchronized (keys) {
            keys.values().forEach(Key::cancel);
            keys.clear();
        }
        fileSystem.untrack(this);
        // Wakes up the threads waiting in take(), which then find the service closed.
        signalled.add(new Key(fileSystem.workingDirectory(), fileSystem.workingDirectoryNode()));
    }

    /** A file a directory had at the last poll, and when it was last modified then. */
    private record Entry(Node node, FileTime lastModifiedTime) {}

    /** The registration of a directory: its events since it was last reset. */
    private final class Key implements WatchKey {

        private final MemoryPath path;
        private final Directory directory;
        private final List<WatchEvent<?>> events = new ArrayList<>();
        private Set<WatchEvent.Kind<?>> kinds = Set.of();
        private Map<String, Entry> entries = Map.of();
        private boolean valid = true;
        private boolean ready = true;

        Key(MemoryPath path, Directory directory) {
            this.path = path;
            this.directory = directory;
        }

        synchronized void watch(Set<WatchEvent.Kind<?>> watched, Map<String, Entry> snapshot) {
            kinds = Set.copyOf(watched);
            entries = snapshot;
        }

        synchronized void compare(Map<String, Entry> snapshot) {
            for (Map.Entry<String, Entry> now : snapshot.entrySet()) {
                Entry before = entries.get(now.getKey());
                if (before == null || before.node() != now.getValue().node()) {
                    event(ENTRY_CREATE, now.getKey());
                } else if (!before.lastModifiedTime().equals(now.getValue().lastModifiedTime())) {
                    event(ENTRY_MODIFY, now.getKey());
                }
            }
            for (Map.Entry<String, Entry> before : entries.entrySet()) {
                Entry now = snapshot.get(before.getKey());
                if (now == null || now.node() != before.getValue().node()) {
                    event(ENTRY_DELETE, before.getKey());
                }
            }
            entries = snapshot;
        }

        private void event(WatchEvent.Kind<Path> kind, String name) {
            if (!kinds.contains(kind)) {
                return;
            }
            events.add(new Event(kind, new MemoryPath(fileSystem, null, List.of(name))));
            if (ready) {
                ready = false;
                signalled.add(this);
            }
        }

        @Override
        public boolean isValid() {
            synchronized (this) {
                return valid && open && fileSystem.isOpen();
            }
        }

        @Override
        public synchronized List<WatchEvent<?>> pollEvents() {
            List<WatchEvent<?>> polled = List.copyOf(events);
            events.clear();
            return polled;
        }

        @Override
        public synchronized boolean reset() {
            if (!isValid()) {
                return false;
            }
            if (events.isEmpty()) {
                ready = true;
            } else {
                signalled.add(this);
            }
            return true;
        }

        @Override
        public synchronized void cancel() {
            valid = false;
        }

        @Override
        public Watchable watchable() {
            return path;
        }
    }

    /** An event: its kind, and the name of the entry it is about. */
    private record Event(WatchEvent.Kind<Path> kind, Path context) implements WatchEvent<Path> {

        @Override
        public int count() {
            return 1;
        }
    }
}
