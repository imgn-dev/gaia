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
    // As the JDK: past that many events, a key drops them for one OVERFLOW event.
    private static final int MAX_EVENTS = 512;

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
                if (key.directory.links() == 0) {
                    key.deleted();
                }
            }
        }
    }

    private void forget(Key key) {
        synchronized (keys) {
            keys.remove(key.directory, key);
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
            if (key != null) {
                // Passes the wake-up on to the next thread waiting in take().
                signalled.add(key);
            }
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
        List<Key> registered;
        synchronized (keys) {
            registered = new ArrayList<>(keys.values());
            keys.clear();
        }
        registered.forEach(Key::cancel);
        fileSystem.untrack(this);
        // Wakes up the threads waiting in take(), which then find the service closed, one after the other.
        signalled.add(new Key(fileSystem.workingDirectory(), fileSystem.workingDirectoryNode()));
    }

    /** A file a directory had at the last poll, and when it was last modified then. */
    private record Entry(Node node, FileTime lastModifiedTime) {}

    /** The registration of a directory: its events since it was last reset. */
    private final class Key implements WatchKey {

        private final MemoryPath path;
        private final Directory directory;
        private final List<Event<?>> events = new ArrayList<>();
        // The last event of each entry, when it is a modification: later ones only count there.
        private final Map<Path, Event<?>> lastModifications = new HashMap<>();
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

        // Adds an event, as the JDK does: a repeat of the last event, or of the last modification of an entry, is
        // counted there; past the maximum, the events give way to an OVERFLOW event that counts the next ones.
        private void event(WatchEvent.Kind<Path> kind, String name) {
            if (!kinds.contains(kind)) {
                return;
            }
            Path context = new MemoryPath(fileSystem, null, List.of(name));
            Event<?> repeated = repeated(kind, context);
            if (repeated != null) {
                repeated.increment();
            } else if (events.size() >= MAX_EVENTS) {
                events.clear();
                lastModifications.clear();
                add(new Event<>(OVERFLOW, null));
            } else {
                Event<Path> event = new Event<>(kind, context);
                if (kind.equals(ENTRY_MODIFY)) {
                    lastModifications.put(context, event);
                }
                add(event);
            }
        }

        private @Nullable Event<?> repeated(WatchEvent.Kind<Path> kind, Path context) {
            Event<?> last = events.isEmpty() ? null : events.getLast();
            if (last != null
                    && (last.kind().equals(OVERFLOW) || (last.kind().equals(kind) && context.equals(last.context())))) {
                return last;
            }
            if (!kind.equals(ENTRY_MODIFY)) {
                // The last event of the entry is no longer a modification.
                lastModifications.remove(context);
            }
            return kind.equals(ENTRY_MODIFY) ? lastModifications.get(context) : null;
        }

        private void add(Event<?> event) {
            events.add(event);
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
            lastModifications.clear();
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

        // Cancels the key of a directory that was deleted, and signals it, so that its thread sees it.
        void deleted() {
            synchronized (this) {
                valid = false;
                if (ready) {
                    ready = false;
                    signalled.add(this);
                }
            }
            forget(this);
        }

        // Not synchronized as a whole: registering holds the keys, then a key.
        @Override
        public void cancel() {
            synchronized (this) {
                valid = false;
            }
            forget(this);
        }

        @Override
        public Watchable watchable() {
            return path;
        }
    }

    /**
     * An event: its kind, the name of the entry it is about, if any, and how many times it happened. Its key counts its
     * repeats until it is polled, under the monitor of the key.
     */
    private static final class Event<T> implements WatchEvent<T> {

        private final WatchEvent.Kind<T> kind;
        private final @Nullable T context;
        private int count = 1;

        Event(WatchEvent.Kind<T> kind, @Nullable T context) {
            this.kind = kind;
            this.context = context;
        }

        void increment() {
            count++;
        }

        @Override
        public WatchEvent.Kind<T> kind() {
            return kind;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public @Nullable T context() {
            return context;
        }
    }
}
