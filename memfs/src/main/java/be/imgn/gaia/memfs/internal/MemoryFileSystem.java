package be.imgn.gaia.memfs.internal;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.memfs.Configuration;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

/**
 * A file system in memory. Its files live as long as it is open; closing it closes its channels, streams and watch
 * services, and frees its name.
 */
public final class MemoryFileSystem extends FileSystem {

    private final MemoryFileSystemProvider provider;
    private final String name;
    private final Configuration configuration;
    private final Syntax syntax;
    private final Names names;
    private final Disk disk;
    private final MemoryFileStore store;
    private final Principals principals = new Principals();
    private final AccessControl access;
    private final AtomicLong ids = new AtomicLong();
    private final FileTree tree;
    private final FileOperations operations;
    private final Attributes attributes;
    private final MemoryPath workingDirectory;
    private final Set<Closeable> resources = ConcurrentHashMap.newKeySet();
    private final Directory workingDirectoryNode;
    private volatile boolean open = true;

    MemoryFileSystem(MemoryFileSystemProvider provider, String name, Configuration configuration) {
        this.provider = provider;
        this.name = name;
        this.configuration = configuration;
        this.syntax = Syntax.of(configuration.style());
        this.names = new Names(
                configuration.nameMatching(),
                configuration.unicodeNormalization().orElse(null));
        this.disk = new Disk(configuration.blockSize(), configuration.maxSize());
        this.store = new MemoryFileStore(name, disk, configuration.attributeViews());
        this.access = new AccessControl(configuration);
        this.tree = new FileTree(this);
        this.attributes = new Attributes(this);
        this.operations = new FileOperations(this);
        this.workingDirectory = MemoryPath.parse(this, configuration.workingDirectory());
        for (String root : configuration.roots()) {
            Directory directory = new Directory(nextId(), now(), root);
            attributes.initialize(directory, List.of());
            directory.link();
            tree.addRoot(root, directory);
        }
        this.workingDirectoryNode = operations.createWorkingDirectory(workingDirectory);
    }

    String name() {
        return name;
    }

    Configuration configuration() {
        return configuration;
    }

    Syntax syntax() {
        return syntax;
    }

    Names names() {
        return names;
    }

    Disk disk() {
        return disk;
    }

    MemoryFileStore store() {
        return store;
    }

    Principals principals() {
        return principals;
    }

    AccessControl access() {
        return access;
    }

    FileTree tree() {
        return tree;
    }

    FileOperations operations() {
        return operations;
    }

    Attributes attributes() {
        return attributes;
    }

    MemoryPath workingDirectory() {
        return workingDirectory;
    }

    Directory workingDirectoryNode() {
        return workingDirectoryNode;
    }

    long nextId() {
        return ids.incrementAndGet();
    }

    FileTime now() {
        return FileTime.from(configuration.clock().instant());
    }

    void checkOpen() {
        if (!open) {
            throw new ClosedFileSystemException();
        }
    }

    // Keeps a channel, stream or watch service, to close it with the file system.
    void track(Closeable resource) {
        resources.add(resource);
    }

    void untrack(Closeable resource) {
        resources.remove(resource);
    }

    URI toUri(MemoryPath absolute) {
        boolean directory = operations.isDirectory(absolute);
        String path = syntax.toUriPath(requireNonNull(absolute.root()), absolute.names());
        if (directory && !path.endsWith("/")) {
            path += "/";
        }
        try {
            return new URI(MemoryFileSystemProvider.SCHEME, name, path, null, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot make a URI of " + absolute, e);
        }
    }

    @Override
    public MemoryFileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        // Under the lock of the tree: a channel, stream or watch service opened under it is tracked before, or not
        // opened at all.
        Lock lock = tree.lock().writeLock();
        lock.lock();
        try {
            if (!open) {
                return;
            }
            open = false;
        } finally {
            lock.unlock();
        }
        provider.closed(this);
        IOException failure = null;
        for (Closeable resource : new ArrayList<>(resources)) {
            try {
                resource.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        resources.clear();
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return syntax.separator();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        checkOpen();
        List<Path> roots = new ArrayList<>();
        for (String root : configuration.roots()) {
            roots.add(new MemoryPath(this, root, List.of()));
        }
        return List.copyOf(roots);
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        checkOpen();
        return List.of(store);
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return configuration.attributeViews();
    }

    @Override
    public MemoryPath getPath(String first, String... more) {
        requireNonNull(first, "first");
        requireNonNull(more, "more");
        StringBuilder path = new StringBuilder(first);
        for (String part : more) {
            if (!requireNonNull(part, "more").isEmpty()) {
                if (!path.isEmpty()) {
                    path.append(syntax.separator());
                }
                path.append(part);
            }
        }
        return MemoryPath.parse(this, path.toString());
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        requireNonNull(syntaxAndPattern, "syntaxAndPattern");
        return Globs.matcher(syntaxAndPattern, configuration);
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        checkOpen();
        return principals;
    }

    @Override
    public WatchService newWatchService() throws IOException {
        return operations.reading(() -> {
            PollingWatchService service = new PollingWatchService(this);
            track(service);
            return service;
        });
    }

    @Override
    public String toString() {
        return MemoryFileSystemProvider.SCHEME + "://" + name;
    }
}
