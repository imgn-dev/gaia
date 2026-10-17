package be.imgn.gaia.memfs.internal;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import org.jspecify.annotations.Nullable;

/**
 * The entries of a directory, as they were when the iterator was obtained, each resolved against the path the stream
 * was opened with. Once the stream is closed, its iterator has no more entries.
 */
class MemoryDirectoryStream implements DirectoryStream<Path> {

    private final MemoryFileSystem fileSystem;
    private final Directory directory;
    private final MemoryPath path;
    private final Filter<? super Path> filter;
    private boolean open = true;
    private boolean iterated;

    MemoryDirectoryStream(
            MemoryFileSystem fileSystem, Directory directory, MemoryPath path, Filter<? super Path> filter) {
        this.fileSystem = fileSystem;
        this.directory = directory;
        this.path = path;
        this.filter = filter;
        fileSystem.track(this);
    }

    final MemoryFileSystem fileSystem() {
        return fileSystem;
    }

    final Directory directory() {
        return directory;
    }

    final synchronized boolean isOpen() {
        return open;
    }

    @Override
    public final synchronized Iterator<Path> iterator() {
        if (!open) {
            throw new IllegalStateException("Directory stream is closed");
        }
        if (iterated) {
            throw new IllegalStateException("Iterator already obtained");
        }
        iterated = true;
        List<String> names =
                directory.entries().stream().map(Directory.Entry::name).toList();
        return new Entries(names.iterator());
    }

    @Override
    public final synchronized void close() {
        open = false;
        fileSystem.untrack(this);
    }

    /** The entries that pass the filter, until the stream is closed. */
    private final class Entries implements Iterator<Path> {

        private final Iterator<String> names;
        private @Nullable Path next;

        Entries(Iterator<String> names) {
            this.names = names;
        }

        @Override
        public boolean hasNext() {
            while (next == null && isOpen() && names.hasNext()) {
                Path entry = path.resolve(new MemoryPath(fileSystem, null, List.of(names.next())));
                try {
                    if (filter.accept(entry)) {
                        next = entry;
                    }
                } catch (IOException e) {
                    throw new DirectoryIteratorException(e);
                }
            }
            return next != null && isOpen();
        }

        @Override
        public Path next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Path entry = requireNonNull(next);
            next = null;
            return entry;
        }
    }
}
