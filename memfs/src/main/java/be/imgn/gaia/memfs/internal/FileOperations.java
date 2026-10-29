package be.imgn.gaia.memfs.internal;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.memfs.Feature;
import be.imgn.gaia.memfs.PathStyle;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The operations on the files of a file system: creating, opening, deleting, copying and moving them. Each one runs
 * under the lock of the tree: reading for lookups, writing for changes.
 */
final class FileOperations {

    private static final int COPY_CHUNK = 64 * 1024;

    private static final Pattern RESERVED =
            Pattern.compile("(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?", Pattern.CASE_INSENSITIVE);

    private final MemoryFileSystem fileSystem;
    private final FileTree tree;

    FileOperations(MemoryFileSystem fileSystem) {
        this.fileSystem = fileSystem;
        this.tree = fileSystem.tree();
    }

    // Creates the working directory and the directories it is in, when the file system is set up.
    Directory createWorkingDirectory(MemoryPath path) {
        Directory directory = requireNonNull(tree.root(requireNonNull(path.root())));
        for (String name : path.names()) {
            Directory.Entry entry = directory.get(fileSystem.names().key(name));
            if (entry != null && entry.node() instanceof Directory existing) {
                directory = existing;
            } else {
                Directory child = new Directory(fileSystem.nextId(), fileSystem.now(), directory, name);
                fileSystem.attributes().initialize(child, List.of());
                link(directory, name, child);
                directory = child;
            }
        }
        return directory;
    }

    <T> T reading(Call<T> call) throws IOException {
        return locked(tree.lock().readLock(), call);
    }

    <T> T writing(Call<T> call) throws IOException {
        return locked(tree.lock().writeLock(), call);
    }

    private <T> T locked(Lock lock, Call<T> call) throws IOException {
        lock.lock();
        try {
            // Under the lock, which closing the file system takes too.
            fileSystem.checkOpen();
            return call.call();
        } finally {
            lock.unlock();
        }
    }

    Node node(@Nullable Directory base, MemoryPath path, boolean followLinks) throws IOException {
        return reading(() -> tree.lookup(base, path, followLinks).existing(path));
    }

    boolean isDirectory(MemoryPath path) {
        try {
            return node(null, path, true) instanceof Directory;
        } catch (IOException e) {
            return false;
        }
    }

    MemoryPath toRealPath(MemoryPath path, boolean followLinks) throws IOException {
        return reading(() -> {
            FileTree.Lookup lookup = tree.lookup(null, path, followLinks);
            Node node = lookup.existing(path);
            if (node instanceof Directory directory) {
                return tree.pathOf(directory);
            }
            return tree.pathOf(lookup.parent()).resolve(new MemoryPath(fileSystem, null, List.of(lookup.name())));
        });
    }

    MemoryFileChannel open(
            @Nullable Directory base,
            MemoryPath path,
            Set<? extends OpenOption> options,
            FileAttribute<?>... attributes)
            throws IOException {
        Options.Open open = Options.Open.of(options);
        @Nullable
        Runnable onClose = open.has(StandardOpenOption.DELETE_ON_CLOSE) ? () -> deleteQuietly(base, path) : null;
        // The channel counts as open before the lock is released: a deletion or the closing of the file system that
        // comes next sees it.
        return writing(() -> new MemoryFileChannel(
                fileSystem,
                openFile(base, path, open, List.of(attributes)),
                path,
                open.has(StandardOpenOption.READ),
                open.has(StandardOpenOption.WRITE),
                open.has(StandardOpenOption.APPEND),
                onClose));
    }

    private RegularFile openFile(
            @Nullable Directory base, MemoryPath path, Options.Open open, List<FileAttribute<?>> attributes)
            throws IOException {
        FileTree.Lookup lookup = tree.lookup(base, path, open.followLinks());
        Node node = lookup.node();
        if (node == null) {
            if (!open.creates()) {
                throw new NoSuchFileException(path.toString());
            }
            RegularFile file = new RegularFile(fileSystem.nextId(), fileSystem.now(), fileSystem.disk());
            create(lookup, path, file, attributes);
            return file;
        }
        if (open.has(StandardOpenOption.WRITE) && open.has(StandardOpenOption.CREATE_NEW)) {
            throw new FileAlreadyExistsException(path.toString());
        }
        RegularFile file = regularFile(node, path);
        if (open.has(StandardOpenOption.READ)) {
            fileSystem.access().check(file, path, AccessMode.READ);
        }
        if (open.has(StandardOpenOption.WRITE)) {
            fileSystem.access().check(file, path, AccessMode.WRITE);
            if (open.has(StandardOpenOption.TRUNCATE_EXISTING)) {
                file.truncate(0);
                file.modified(fileSystem.now());
            }
        }
        return file;
    }

    private static RegularFile regularFile(Node node, MemoryPath path) throws FileSystemException {
        return switch (node) {
            case RegularFile file -> file;
            case Directory directory -> throw new FileSystemException(path.toString(), null, "Is a directory");
            case SymbolicLink link ->
                throw new FileSystemException(
                        path.toString(), null, "Too many levels of symbolic links (NOFOLLOW_LINKS specified)");
        };
    }

    // Adds a new file to the directory of a lookup, with its attributes, if the current user may.
    private void create(FileTree.Lookup lookup, MemoryPath path, Node node, List<FileAttribute<?>> attributes)
            throws IOException {
        Directory parent = lookup.parent();
        fileSystem.access().check(parent, path, AccessMode.WRITE, AccessMode.EXECUTE);
        checkName(lookup.name(), path);
        fileSystem.attributes().initialize(node, attributes);
        link(parent, lookup.name(), node);
    }

    private void checkName(String name, MemoryPath path) throws FileSystemException {
        if (name.length() > fileSystem.configuration().maxNameLength()) {
            throw new FileSystemException(path.toString(), null, "File name too long");
        }
        if (fileSystem.configuration().style() == PathStyle.WINDOWS
                && RESERVED.matcher(name).matches()) {
            throw new FileSystemException(path.toString(), null, "Reserved name");
        }
    }

    private void link(Directory parent, String name, Node node) {
        parent.put(fileSystem.names().key(name), name, node);
        node.link();
        parent.modified(fileSystem.now());
    }

    private void unlink(Directory parent, String name) {
        Directory.Entry entry = parent.remove(fileSystem.names().key(name));
        if (entry != null) {
            entry.node().unlink();
            parent.modified(fileSystem.now());
        }
    }

    void createDirectory(MemoryPath path, FileAttribute<?>... attributes) throws IOException {
        writing(() -> {
            FileTree.Lookup lookup = tree.lookup(null, path, false);
            if (lookup.node() != null) {
                throw new FileAlreadyExistsException(path.toString());
            }
            Directory directory = new Directory(fileSystem.nextId(), fileSystem.now(), lookup.parent(), lookup.name());
            create(lookup, path, directory, List.of(attributes));
            return directory;
        });
    }

    void createSymbolicLink(MemoryPath link, MemoryPath target, FileAttribute<?>... attributes) throws IOException {
        checkFeature(Feature.SYMBOLIC_LINKS, "Symbolic links");
        if (attributes.length > 0) {
            throw new UnsupportedOperationException("Initial attributes not supported for symbolic links");
        }
        writing(() -> {
            FileTree.Lookup lookup = tree.lookup(null, link, false);
            if (lookup.node() != null) {
                throw new FileAlreadyExistsException(link.toString());
            }
            SymbolicLink node = new SymbolicLink(fileSystem.nextId(), fileSystem.now(), target);
            create(lookup, link, node, List.of());
            return node;
        });
    }

    void createLink(MemoryPath link, MemoryPath existing) throws IOException {
        checkFeature(Feature.LINKS, "Links");
        writing(() -> {
            Node node = tree.lookup(null, existing, false).existing(existing);
            if (node instanceof Directory) {
                throw new FileSystemException(link.toString(), existing.toString(), "Operation not permitted");
            }
            FileTree.Lookup lookup = tree.lookup(null, link, false);
            if (lookup.node() != null) {
                throw new FileAlreadyExistsException(link.toString());
            }
            fileSystem.access().check(lookup.parent(), link, AccessMode.WRITE, AccessMode.EXECUTE);
            checkName(lookup.name(), link);
            link(lookup.parent(), lookup.name(), node);
            node.changed(fileSystem.now());
            return node;
        });
    }

    MemoryPath readSymbolicLink(MemoryPath link) throws IOException {
        return reading(() -> {
            if (!(tree.lookup(null, link, false).existing(link) instanceof SymbolicLink node)) {
                throw new NotLinkException(link.toString());
            }
            node.accessed(fileSystem.now());
            return node.target();
        });
    }

    void delete(@Nullable Directory base, MemoryPath path, Deletion deletion) throws IOException {
        writing(() -> {
            FileTree.Lookup lookup = tree.lookup(base, path, false);
            Node node = lookup.existing(path);
            checkDeletion(node, path, deletion);
            fileSystem.access().check(lookup.parent(), path, AccessMode.WRITE, AccessMode.EXECUTE);
            fileSystem.access().checkNotReadOnly(node, path);
            unlink(lookup.parent(), lookup.name());
            return node;
        });
    }

    private static void checkDeletion(Node node, MemoryPath path, Deletion deletion) throws IOException {
        if (node instanceof Directory directory) {
            if (directory.isRoot()) {
                throw new FileSystemException(path.toString(), null, "Cannot delete a root directory");
            }
            if (deletion == Deletion.FILE) {
                throw new FileSystemException(path.toString(), null, "Is a directory");
            }
            if (!directory.isEmpty()) {
                throw new DirectoryNotEmptyException(path.toString());
            }
        } else if (deletion == Deletion.DIRECTORY) {
            throw new NotDirectoryException(path.toString());
        }
    }

    private void deleteQuietly(@Nullable Directory base, MemoryPath path) {
        try {
            if (fileSystem.isOpen()) {
                delete(base, path, Deletion.FILE);
            }
        } catch (IOException e) {
            // The file was deleted or replaced since it was opened: there is nothing left to delete.
        }
    }

    void copy(MemoryPath source, MemoryPath target, Options.Copy options) throws IOException {
        if (options.atomic()) {
            throw new UnsupportedOperationException("ATOMIC_MOVE not supported when copying");
        }
        Snapshot snapshot = reading(() -> snapshot(source, options.followLinks()));
        target.getFileSystem().operations().paste(snapshot, target, options);
    }

    private Snapshot snapshot(MemoryPath source, boolean followLinks) throws IOException {
        Node node = tree.lookup(null, source, followLinks).existing(source);
        return switch (node) {
            case RegularFile file -> {
                fileSystem.access().check(file, source, AccessMode.READ);
                file.accessed(fileSystem.now());
                yield new Snapshot(node, file, null);
            }
            case SymbolicLink link -> new Snapshot(node, null, link.target().toString());
            case Directory directory -> new Snapshot(node, null, null);
        };
    }

    // Creates a copy of a snapshot at a path of this file system.
    private void paste(Snapshot snapshot, MemoryPath target, Options.Copy options) throws IOException {
        writing(() -> {
            FileTree.Lookup lookup = tree.lookup(null, target, false);
            Node existing = lookup.node();
            if (existing == snapshot.node()) {
                return existing;
            }
            if (existing != null) {
                replace(lookup, existing, target, options.replace());
            }
            Node copy = copyOf(snapshot, target);
            create(lookup, target, copy, List.of());
            if (options.copyAttributes()) {
                copy.copyAttributesFrom(snapshot.node());
            }
            return copy;
        });
    }

    private Node copyOf(Snapshot snapshot, MemoryPath target) throws IOException {
        RegularFile content = snapshot.content();
        String linkTarget = snapshot.linkTarget();
        if (content != null) {
            RegularFile file = new RegularFile(fileSystem.nextId(), fileSystem.now(), fileSystem.disk());
            // A chunk at a time: a file can be larger than an array, and needs no copy of it on the heap.
            ByteBuffer chunk = ByteBuffer.allocate(COPY_CHUNK);
            long position = 0;
            int read;
            while ((read = content.read(position, chunk)) > 0) {
                file.write(position, chunk.flip(), false, target.toString());
                chunk.clear();
                position += read;
            }
            return file;
        }
        if (linkTarget != null) {
            return new SymbolicLink(fileSystem.nextId(), fileSystem.now(), MemoryPath.parse(fileSystem, linkTarget));
        }
        FileTree.Lookup lookup = tree.lookup(null, target, false);
        return new Directory(fileSystem.nextId(), fileSystem.now(), lookup.parent(), lookup.name());
    }

    private void replace(FileTree.Lookup lookup, Node existing, MemoryPath target, boolean replace) throws IOException {
        if (!replace) {
            throw new FileAlreadyExistsException(target.toString());
        }
        if (existing instanceof Directory directory) {
            if (directory.isRoot()) {
                throw new FileSystemException(target.toString(), null, "Cannot replace a root directory");
            }
            if (!directory.isEmpty()) {
                throw new DirectoryNotEmptyException(target.toString());
            }
        }
        fileSystem.access().check(lookup.parent(), target, AccessMode.WRITE, AccessMode.EXECUTE);
        unlink(lookup.parent(), lookup.name());
    }

    void move(
            @Nullable Directory sourceBase,
            MemoryPath source,
            @Nullable Directory targetBase,
            MemoryPath target,
            Options.Copy options)
            throws IOException {
        if (target.getFileSystem() != fileSystem) {
            moveAcross(source, target, options);
            return;
        }
        writing(() -> {
            FileTree.Lookup from = tree.lookup(sourceBase, source, false);
            Node node = from.existing(source);
            FileTree.Lookup to = tree.lookup(targetBase, target, false);
            checkMove(node, from, to, source, target, options);
            fileSystem.access().check(from.parent(), source, AccessMode.WRITE, AccessMode.EXECUTE);
            fileSystem.access().check(to.parent(), target, AccessMode.WRITE, AccessMode.EXECUTE);
            Directory.Entry entry =
                    requireNonNull(from.parent().remove(fileSystem.names().key(from.name())));
            from.parent().modified(fileSystem.now());
            String name = nameAsWritten(to, target);
            to.parent().put(fileSystem.names().key(name), name, entry.node());
            to.parent().modified(fileSystem.now());
            if (node instanceof Directory directory) {
                directory.moveTo(to.parent(), name);
            }
            node.changed(fileSystem.now());
            return node;
        });
    }

    // The name a path gives its last file, rather than the one an entry it matches has: "README" for "readme".
    private static String nameAsWritten(FileTree.Lookup lookup, MemoryPath path) {
        List<String> names = path.names();
        return names.isEmpty() || path.isEmpty() ? lookup.name() : names.getLast();
    }

    private void checkMove(
            Node node,
            FileTree.Lookup from,
            FileTree.Lookup to,
            MemoryPath source,
            MemoryPath target,
            Options.Copy options)
            throws IOException {
        if (node instanceof Directory directory) {
            if (directory.isRoot()) {
                throw new FileSystemException(source.toString(), null, "Cannot move a root directory");
            }
            if (to.parent().isIn(directory)) {
                throw new FileSystemException(source.toString(), target.toString(), "Invalid argument");
            }
        }
        Node existing = to.node();
        boolean sameEntry = existing == node
                && from.parent() == to.parent()
                && fileSystem.names().key(from.name()).equals(fileSystem.names().key(to.name()));
        if (existing != null && !sameEntry) {
            replace(to, existing, target, options.replace() || options.atomic());
        }
    }

    private void moveAcross(MemoryPath source, MemoryPath target, Options.Copy options) throws IOException {
        if (options.atomic()) {
            throw new AtomicMoveNotSupportedException(
                    source.toString(), target.toString(), "Not on the same file system");
        }
        if (node(null, source, false) instanceof Directory directory && !directory.isEmpty()) {
            throw new DirectoryNotEmptyException(source.toString());
        }
        copy(source, target, new Options.Copy(options.replace(), true, false, false));
        delete(null, source, Deletion.ANY);
    }

    boolean isSameFile(MemoryPath path, Path other) throws IOException {
        if (path.equals(other)) {
            return true;
        }
        if (!(other instanceof MemoryPath that) || that.getFileSystem() != fileSystem) {
            return false;
        }
        return reading(() -> tree.lookup(null, path, true).existing(path)
                == tree.lookup(null, that, true).existing(that));
    }

    boolean isHidden(MemoryPath path) throws IOException {
        if (fileSystem.configuration().style() == PathStyle.UNIX) {
            MemoryPath name = path.getFileName();
            return name != null && name.toString().startsWith(".");
        }
        return Boolean.TRUE.equals(node(null, path, true).attribute("dos:hidden"));
    }

    void checkAccess(MemoryPath path, AccessMode... modes) throws IOException {
        reading(() -> {
            Node node = tree.lookup(null, path, true).existing(path);
            fileSystem.access().check(node, path, modes);
            return node;
        });
    }

    DirectoryStream<Path> newDirectoryStream(
            @Nullable Directory base, MemoryPath path, DirectoryStream.Filter<? super Path> filter, boolean followLinks)
            throws IOException {
        return newDirectoryStream(base, path, path, filter, followLinks);
    }

    // The stream resolves its entries against streamPath, which is path unless path is relative to another stream.
    DirectoryStream<Path> newDirectoryStream(
            @Nullable Directory base,
            MemoryPath path,
            MemoryPath streamPath,
            DirectoryStream.Filter<? super Path> filter,
            boolean followLinks)
            throws IOException {
        return reading(() -> {
            Node node = tree.lookup(base, path, followLinks).existing(path);
            if (!(node instanceof Directory directory)) {
                throw new NotDirectoryException(path.toString());
            }
            fileSystem.access().check(directory, path, AccessMode.READ);
            directory.accessed(fileSystem.now());
            return fileSystem.configuration().features().contains(Feature.SECURE_DIRECTORY_STREAM)
                    ? new MemorySecureDirectoryStream(fileSystem, directory, streamPath, filter)
                    : new MemoryDirectoryStream(fileSystem, directory, streamPath, filter);
        });
    }

    void checkFeature(Feature feature, String what) {
        if (!fileSystem.configuration().features().contains(feature)) {
            throw new UnsupportedOperationException(what + " are not supported by " + fileSystem + " ("
                    + feature.name().toLowerCase(Locale.ROOT) + ")");
        }
    }

    /** What a deletion accepts: any file, only a file that is not a directory, or only a directory. */
    enum Deletion {
        ANY,
        FILE,
        DIRECTORY
    }

    /** An operation that may fail with an {@link IOException}. */
    @FunctionalInterface
    interface Call<T> {

        T call() throws IOException;
    }

    /** What a copy takes from its source: a regular file to read the content of, or the target of a symbolic link. */
    private static final class Snapshot {

        private final Node node;
        private final @Nullable RegularFile content;
        private final @Nullable String linkTarget;

        Snapshot(Node node, @Nullable RegularFile content, @Nullable String linkTarget) {
            this.node = node;
            this.content = content;
            this.linkTarget = linkTarget;
        }

        Node node() {
            return node;
        }

        @Nullable
        RegularFile content() {
            return content;
        }

        @Nullable
        String linkTarget() {
            return linkTarget;
        }
    }
}
