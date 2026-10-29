package be.imgn.gaia.memfs.internal;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.memfs.Configuration;
import be.imgn.gaia.memfs.Feature;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The provider of the {@code mem} URI scheme. A URI {@code mem://name/dir/file} names the path {@code /dir/file} of the
 * open file system {@code name}; on a Windows-style file system, {@code mem://name/C:/dir/file} names
 * {@code C:\dir\file}.
 */
public final class MemoryFileSystemProvider extends FileSystemProvider {

    /** The URI scheme: {@code mem}. */
    public static final String SCHEME = "mem";

    /** The key of the configuration in the environment of {@link #newFileSystem(URI, Map)}. */
    public static final String CONFIGURATION = "configuration";

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._~-]+");
    // The attributes only the owner of a file changes.
    // The views whose attributes only the owner sets: the times of every view among them, as with utimes.
    private static final Pattern OWNED = Pattern.compile("(owner|posix|unix|acl|basic):");
    private static final Pattern WRITABLE = Pattern.compile("(dos|user):");
    private static final MemoryFileSystemProvider FALLBACK = new MemoryFileSystemProvider();

    private final Map<String, MemoryFileSystem> fileSystems = new ConcurrentHashMap<>();
    private @Nullable ExecutorService executor;

    /** Creates the provider, as the {@link java.util.ServiceLoader} does. */
    public MemoryFileSystemProvider() {}

    /**
     * Returns the installed provider of the {@code mem} scheme, or one of its own if it is not installed, as when this
     * module is loaded by a class loader the JDK does not search for providers.
     *
     * @return the provider
     */
    public static MemoryFileSystemProvider instance() {
        for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
            if (provider instanceof MemoryFileSystemProvider memory) {
                return memory;
            }
        }
        return FALLBACK;
    }

    @Override
    public String getScheme() {
        return SCHEME;
    }

    /**
     * Creates a file system.
     *
     * @param name the name of the file system, which its URIs have as authority
     * @param configuration the configuration
     * @return the file system
     * @throws IllegalArgumentException if the name has another character than letters, digits and {@code . _ ~ -}
     * @throws FileSystemAlreadyExistsException if a file system of that name is open
     */
    public MemoryFileSystem newFileSystem(String name, Configuration configuration) {
        requireNonNull(name, "name");
        requireNonNull(configuration, "configuration");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("A name has only letters, digits and the characters . _ ~ -: " + name);
        }
        MemoryFileSystem fileSystem = new MemoryFileSystem(this, name, configuration);
        if (fileSystems.putIfAbsent(name, fileSystem) != null) {
            throw new FileSystemAlreadyExistsException(name);
        }
        return fileSystem;
    }

    @Override
    public MemoryFileSystem newFileSystem(URI uri, Map<String, ?> env) {
        requireNonNull(env, "env");
        Object configuration = env.get(CONFIGURATION);
        if (configuration != null && !(configuration instanceof Configuration)) {
            throw new IllegalArgumentException("The configuration must be a Configuration: " + configuration);
        }
        return newFileSystem(nameOf(uri), configuration == null ? Configuration.unix() : (Configuration) configuration);
    }

    void closed(MemoryFileSystem fileSystem) {
        fileSystems.remove(fileSystem.name(), fileSystem);
    }

    @Override
    public MemoryFileSystem getFileSystem(URI uri) {
        String name = nameOf(uri);
        MemoryFileSystem fileSystem = fileSystems.get(name);
        if (fileSystem == null) {
            throw new FileSystemNotFoundException(uri.toString());
        }
        return fileSystem;
    }

    @Override
    public MemoryPath getPath(URI uri) {
        MemoryFileSystem fileSystem = getFileSystem(uri);
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("A mem URI needs a path: " + uri);
        }
        MemoryPath parsed = MemoryPath.parse(fileSystem, fileSystem.syntax().fromUriPath(path));
        if (!parsed.isAbsolute()) {
            throw new IllegalArgumentException("Not an absolute path: " + uri);
        }
        return parsed;
    }

    private static String nameOf(URI uri) {
        requireNonNull(uri, "uri");
        if (!SCHEME.equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Not a mem URI: " + uri);
        }
        String name = uri.getAuthority();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("A mem URI names its file system: " + uri);
        }
        return name;
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        MemoryPath checked = check(path);
        return checked.getFileSystem().operations().open(null, checked, options, attrs);
    }

    @Override
    public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        MemoryPath checked = check(path);
        FileOperations operations = checked.getFileSystem().operations();
        operations.checkFeature(Feature.FILE_CHANNEL, "File channels");
        return operations.open(null, checked, options, attrs);
    }

    @Override
    public AsynchronousFileChannel newAsynchronousFileChannel(
            Path path, Set<? extends OpenOption> options, @Nullable ExecutorService executor, FileAttribute<?>... attrs)
            throws IOException {
        MemoryPath checked = check(path);
        FileOperations operations = checked.getFileSystem().operations();
        operations.checkFeature(Feature.FILE_CHANNEL, "File channels");
        MemoryFileChannel channel = operations.open(null, checked, options, attrs);
        return new MemoryAsynchronousFileChannel(channel, executor == null ? sharedExecutor() : executor);
    }

    private synchronized ExecutorService sharedExecutor() {
        ExecutorService shared = executor;
        if (shared == null) {
            shared = Executors.newCachedThreadPool(task -> {
                Thread thread = new Thread(task, "memfs-io");
                thread.setDaemon(true);
                return thread;
            });
            executor = shared;
        }
        return shared;
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
            throws IOException {
        requireNonNull(filter, "filter");
        MemoryPath checked = check(dir);
        return checked.getFileSystem().operations().newDirectoryStream(null, checked, filter, true);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        MemoryPath checked = check(dir);
        checked.getFileSystem().operations().createDirectory(checked, attrs);
    }

    @Override
    public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
        MemoryPath checked = check(link);
        MemoryPath checkedTarget = check(target);
        if (checkedTarget.getFileSystem() != checked.getFileSystem()) {
            throw new ProviderMismatchException("The target is on another file system: " + target);
        }
        checked.getFileSystem().operations().createSymbolicLink(checked, checkedTarget, attrs);
    }

    @Override
    public void createLink(Path link, Path existing) throws IOException {
        MemoryPath checked = check(link);
        MemoryPath checkedExisting = check(existing);
        if (checkedExisting.getFileSystem() != checked.getFileSystem()) {
            throw new ProviderMismatchException("The file is on another file system: " + existing);
        }
        checked.getFileSystem().operations().createLink(checked, checkedExisting);
    }

    @Override
    public void delete(Path path) throws IOException {
        MemoryPath checked = check(path);
        checked.getFileSystem().operations().delete(null, checked, FileOperations.Deletion.ANY);
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        MemoryPath checked = check(link);
        return checked.getFileSystem().operations().readSymbolicLink(checked);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        MemoryPath checked = check(source);
        checked.getFileSystem().operations().copy(checked, check(target), Options.Copy.of(options));
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        MemoryPath checked = check(source);
        checked.getFileSystem().operations().move(null, checked, null, check(target), Options.Copy.of(options));
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        MemoryPath checked = check(path);
        return checked.getFileSystem().operations().isSameFile(checked, requireNonNull(path2, "path2"));
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        MemoryPath checked = check(path);
        return checked.getFileSystem().operations().isHidden(checked);
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        MemoryPath checked = check(path);
        checked.getFileSystem().operations().node(null, checked, true);
        return checked.getFileSystem().store();
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        MemoryPath checked = check(path);
        checked.getFileSystem().operations().checkAccess(checked, modes);
    }

    @Override
    public <V extends FileAttributeView> @Nullable V getFileAttributeView(
            Path path, Class<V> type, LinkOption... options) {
        requireNonNull(type, "type");
        MemoryPath checked = check(path);
        MemoryFileSystem fileSystem = checked.getFileSystem();
        return AttributeViews.view(
                fileSystem, Locator.of(fileSystem, null, checked, Options.followLinks(options)), type);
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
            throws IOException {
        requireNonNull(type, "type");
        MemoryPath checked = check(path);
        MemoryFileSystem fileSystem = checked.getFileSystem();
        Attributes attributes = fileSystem.attributes();
        String view = type == BasicFileAttributes.class
                ? Views.BASIC
                : type == PosixFileAttributes.class ? Views.POSIX : type == DosFileAttributes.class ? Views.DOS : null;
        if (view == null || !attributes.supports(view)) {
            throw new UnsupportedOperationException("Unsupported attributes: " + type.getName());
        }
        Node node = fileSystem.operations().node(null, checked, Options.followLinks(options));
        return type.cast(
                switch (view) {
                    case Views.POSIX -> attributes.posix(node);
                    case Views.DOS -> attributes.dos(node);
                    default -> attributes.basic(node);
                });
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        requireNonNull(attributes, "attributes");
        MemoryPath checked = check(path);
        MemoryFileSystem fileSystem = checked.getFileSystem();
        boolean followLinks = Options.followLinks(options);
        return fileSystem.operations().reading(() -> {
            Node node = fileSystem.tree().lookup(null, checked, followLinks).existing(checked);
            return fileSystem.attributes().read(node, attributes);
        });
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        requireNonNull(attribute, "attribute");
        requireNonNull(value, "value");
        MemoryPath checked = check(path);
        MemoryFileSystem fileSystem = checked.getFileSystem();
        fileSystem.operations().writing(() -> {
            Node node = fileSystem
                    .tree()
                    .lookup(null, checked, Options.followLinks(options))
                    .existing(checked);
            if (OWNED.matcher(attribute).lookingAt() || !attribute.contains(":")) {
                fileSystem.access().checkOwner(node, checked);
            } else if (WRITABLE.matcher(attribute).lookingAt()) {
                fileSystem.access().checkAttributesWritable(node, checked);
            }
            fileSystem.attributes().set(node, attribute, value, false);
            return node;
        });
    }

    private static MemoryPath check(Path path) {
        requireNonNull(path, "path");
        if (!(path instanceof MemoryPath memoryPath)) {
            throw new ProviderMismatchException("Not a path of a memory file system: " + path);
        }
        return memoryPath;
    }
}
