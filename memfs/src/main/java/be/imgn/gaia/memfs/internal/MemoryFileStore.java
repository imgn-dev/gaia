package be.imgn.gaia.memfs.internal;

import static java.util.Objects.requireNonNull;

import java.nio.file.FileStore;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** The one store of a memory file system: its capacity, and the attribute views it supports. */
final class MemoryFileStore extends FileStore {

    private static final Map<Class<? extends FileAttributeView>, String> VIEWS = Map.of(
            BasicFileAttributeView.class, Views.BASIC,
            FileOwnerAttributeView.class, Views.OWNER,
            PosixFileAttributeView.class, Views.POSIX,
            DosFileAttributeView.class, Views.DOS,
            AclFileAttributeView.class, Views.ACL,
            UserDefinedFileAttributeView.class, Views.USER);

    private final String name;
    private final Disk disk;
    private final Set<String> views;

    MemoryFileStore(String name, Disk disk, Set<String> views) {
        this.name = name;
        this.disk = disk;
        this.views = views;
    }

    // The name of the view of a class, if the store supports it.
    static @Nullable String viewName(Class<? extends FileAttributeView> type) {
        return VIEWS.get(type);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "memory";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public long getTotalSpace() {
        return disk.totalSpace();
    }

    @Override
    public long getUsableSpace() {
        return disk.unallocatedSpace();
    }

    @Override
    public long getUnallocatedSpace() {
        return disk.unallocatedSpace();
    }

    @Override
    public long getBlockSize() {
        return disk.blockSize();
    }

    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        requireNonNull(type, "type");
        String view = viewName(type);
        return view != null && views.contains(view);
    }

    @Override
    public boolean supportsFileAttributeView(String view) {
        requireNonNull(view, "view");
        return views.contains(view);
    }

    @Override
    public <V extends FileStoreAttributeView> @Nullable V getFileStoreAttributeView(Class<V> type) {
        requireNonNull(type, "type");
        return null;
    }

    @Override
    public Object getAttribute(String attribute) {
        requireNonNull(attribute, "attribute");
        return switch (attribute) {
            case "totalSpace" -> getTotalSpace();
            case "usableSpace" -> getUsableSpace();
            case "unallocatedSpace" -> getUnallocatedSpace();
            default -> throw new UnsupportedOperationException("Unknown file store attribute: " + attribute);
        };
    }

    @Override
    public String toString() {
        return name + " (memory)";
    }
}
