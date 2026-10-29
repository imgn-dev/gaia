package be.imgn.gaia.memfs.internal;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jspecify.annotations.Nullable;

/**
 * The directories and files of a file system, from their roots, and the lock that guards their structure. Paths are
 * looked up name by name, following symbolic links, as the operating system does: {@code ..} goes to the directory a
 * directory is in, after symbolic links are followed.
 */
final class FileTree {

    // As Linux: past that many symbolic links in one lookup, there is a loop.
    private static final int MAX_LINKS = 40;

    private final MemoryFileSystem fileSystem;
    private final Map<String, Directory> roots = new LinkedHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    FileTree(MemoryFileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    ReadWriteLock lock() {
        return lock;
    }

    void addRoot(String root, Directory directory) {
        roots.put(fileSystem.syntax().rootKey(root), directory);
    }

    @Nullable
    Directory root(String root) {
        return roots.get(fileSystem.syntax().rootKey(root));
    }

    /**
     * Looks a path up. A relative path is looked up from a directory, or from the working directory.
     *
     * @param base the directory a relative path starts from, or {@code null} for the working directory
     * @param path the path
     * @param followLinks whether a symbolic link at the end of the path is followed
     * @return the directory the last name is in, that name, and the file it names, if any
     * @throws NoSuchFileException if a directory on the way does not exist
     * @throws FileSystemException if a file on the way is not a directory, or symbolic links loop
     */
    Lookup lookup(@Nullable Directory base, MemoryPath path, boolean followLinks) throws IOException {
        if (path.root() == null) {
            Directory start = base == null ? fileSystem.workingDirectoryNode() : base;
            return walk(start, names(path), path, followLinks, new int[1]);
        }
        MemoryPath absolute = path.absolute();
        return walk(rootDirectory(absolute, path), names(absolute), path, followLinks, new int[1]);
    }

    private Directory rootDirectory(MemoryPath absolute, MemoryPath path) throws NoSuchFileException {
        Directory directory = root(requireNonNull(absolute.root()));
        if (directory == null) {
            throw new NoSuchFileException(path.toString());
        }
        return directory;
    }

    private static List<String> names(MemoryPath path) {
        return path.isEmpty() ? List.of() : path.names();
    }

    private Lookup walk(Directory start, List<String> names, MemoryPath path, boolean followLinks, int[] hops)
            throws IOException {
        Directory directory = start;
        if (names.isEmpty()) {
            return new Lookup(directory.parent(), directory.name(), directory);
        }
        for (int i = 0; i < names.size() - 1; i++) {
            directory = step(directory, names.get(i), path, hops);
        }
        return last(directory, names.getLast(), path, followLinks, hops);
    }

    // Goes into the directory a name names, through symbolic links.
    private Directory step(Directory directory, String name, MemoryPath path, int[] hops) throws IOException {
        return switch (name) {
            case "." -> directory;
            case ".." -> directory.parent();
            default -> {
                fileSystem.access().checkSearch(directory, path);
                Directory.Entry entry = directory.get(fileSystem.names().key(name));
                if (entry == null) {
                    throw new NoSuchFileException(path.toString());
                }
                Node node = entry.node() instanceof SymbolicLink link
                        ? follow(directory, link, path, hops).node()
                        : entry.node();
                if (node == null) {
                    throw new NoSuchFileException(path.toString());
                }
                if (!(node instanceof Directory child)) {
                    throw new FileSystemException(path.toString(), null, "Not a directory");
                }
                yield child;
            }
        };
    }

    private Lookup last(Directory directory, String name, MemoryPath path, boolean followLinks, int[] hops)
            throws IOException {
        if (".".equals(name) || "..".equals(name)) {
            Directory named = step(directory, name, path, hops);
            return new Lookup(named.parent(), named.name(), named);
        }
        fileSystem.access().checkSearch(directory, path);
        Directory.Entry entry = directory.get(fileSystem.names().key(name));
        if (entry == null) {
            return new Lookup(directory, name, null);
        }
        if (followLinks && entry.node() instanceof SymbolicLink link) {
            return follow(directory, link, path, hops);
        }
        return new Lookup(directory, entry.name(), entry.node());
    }

    private Lookup follow(Directory directory, SymbolicLink link, MemoryPath path, int[] hops) throws IOException {
        hops[0]++;
        if (hops[0] > MAX_LINKS) {
            throw new FileSystemException(path.toString(), null, "Too many levels of symbolic links");
        }
        MemoryPath target = link.target();
        if (target.root() == null) {
            return walk(directory, names(target), path, true, hops);
        }
        MemoryPath absolute = target.absolute();
        return walk(rootDirectory(absolute, path), names(absolute), path, true, hops);
    }

    // The absolute path of a directory, from the names its entries have.
    MemoryPath pathOf(Directory directory) {
        List<String> names = new ArrayList<>();
        Directory current = directory;
        while (!current.isRoot()) {
            names.addFirst(current.name());
            current = current.parent();
        }
        return new MemoryPath(fileSystem, current.name(), names);
    }

    /**
     * A path looked up: the directory its last name is in, that name as the directory has it, and the file it names, or
     * {@code null} if there is none. For a root, the directory and the file are the root itself.
     */
    record Lookup(Directory parent, String name, @Nullable Node node) {

        Node existing(MemoryPath path) throws NoSuchFileException {
            if (node == null) {
                throw new NoSuchFileException(path.toString());
            }
            return node;
        }
    }
}
