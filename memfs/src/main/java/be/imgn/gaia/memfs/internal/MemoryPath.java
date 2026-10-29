package be.imgn.gaia.memfs.internal;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.memfs.PathStyle;
import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A path of a memory file system: an optional root and names. The empty path has one empty name, as in the default file
 * system. Paths of a Windows-style file system compare ignoring case, as there.
 */
public final class MemoryPath implements Path {

    private final MemoryFileSystem fileSystem;
    private final @Nullable String root;
    private final List<String> names;
    private @Nullable String string;

    MemoryPath(MemoryFileSystem fileSystem, @Nullable String root, List<String> names) {
        this.fileSystem = fileSystem;
        this.root = root;
        this.names = List.copyOf(names);
    }

    static MemoryPath parse(MemoryFileSystem fileSystem, String path) {
        Syntax.Parsed parsed = fileSystem.syntax().parse(path);
        return new MemoryPath(fileSystem, parsed.root(), parsed.names());
    }

    @Nullable
    String root() {
        return root;
    }

    List<String> names() {
        return names;
    }

    // Whether this is the empty path, "".
    boolean isEmpty() {
        return root == null && names.size() == 1 && names.getFirst().isEmpty();
    }

    @Override
    public MemoryFileSystem getFileSystem() {
        return fileSystem;
    }

    @Override
    public boolean isAbsolute() {
        return root != null && fileSystem.syntax().isAbsolute(root);
    }

    @Override
    public @Nullable MemoryPath getRoot() {
        return root == null ? null : new MemoryPath(fileSystem, root, List.of());
    }

    @Override
    public @Nullable MemoryPath getFileName() {
        return names.isEmpty() ? null : new MemoryPath(fileSystem, null, List.of(names.getLast()));
    }

    @Override
    public @Nullable MemoryPath getParent() {
        if (names.isEmpty() || (names.size() == 1 && root == null)) {
            return null;
        }
        return new MemoryPath(fileSystem, root, names.subList(0, names.size() - 1));
    }

    @Override
    public int getNameCount() {
        return names.size();
    }

    @Override
    public MemoryPath getName(int index) {
        if (index < 0 || index >= names.size()) {
            throw new IllegalArgumentException("No name at " + index + " in " + this);
        }
        return new MemoryPath(fileSystem, null, List.of(names.get(index)));
    }

    @Override
    public MemoryPath subpath(int beginIndex, int endIndex) {
        if (beginIndex < 0 || endIndex > names.size() || beginIndex >= endIndex) {
            throw new IllegalArgumentException("Invalid subpath [" + beginIndex + ", " + endIndex + ") of " + this);
        }
        return new MemoryPath(fileSystem, null, names.subList(beginIndex, endIndex));
    }

    @Override
    public boolean startsWith(Path other) {
        MemoryPath path = check(other);
        return sameRoot(path)
                && path.names.size() <= names.size()
                && sameNames(names.subList(0, path.names.size()), path.names)
                && !(path.isEmpty() && !isEmpty());
    }

    @Override
    public boolean endsWith(Path other) {
        MemoryPath path = check(other);
        if (path.root != null) {
            return equals(path);
        }
        if (path.isEmpty()) {
            return isEmpty();
        }
        return path.names.size() <= names.size()
                && sameNames(names.subList(names.size() - path.names.size(), names.size()), path.names);
    }

    @Override
    public MemoryPath normalize() {
        List<String> normalized = new ArrayList<>();
        for (String name : names) {
            if (".".equals(name)) {
                continue;
            }
            if ("..".equals(name) && !normalized.isEmpty() && !"..".equals(normalized.getLast())) {
                normalized.removeLast();
            } else if (!("..".equals(name) && root != null)) {
                normalized.add(name);
            }
        }
        if (normalized.isEmpty() && root == null) {
            normalized.add("");
        }
        return new MemoryPath(fileSystem, root, normalized);
    }

    @Override
    public MemoryPath resolve(Path other) {
        MemoryPath path = check(other);
        if (path.isAbsolute() || isEmpty()) {
            return path;
        }
        if (path.isEmpty()) {
            return this;
        }
        if (path.root != null) {
            return resolveRooted(path);
        }
        List<String> joined = new ArrayList<>(names);
        joined.addAll(path.names);
        return new MemoryPath(fileSystem, root, joined);
    }

    // A Windows path with a root that is not absolute: "\dir" on the drive of this path, "C:dir" from this path if it
    // is on the same drive.
    private MemoryPath resolveRooted(MemoryPath path) {
        String otherRoot = requireNonNull(path.root);
        if ("\\".equals(otherRoot)) {
            String drive = root == null ? null : driveOf(root);
            return drive == null ? path : new MemoryPath(fileSystem, drive + "\\", path.names);
        }
        if (root != null && otherRoot.equalsIgnoreCase(driveOf(root))) {
            List<String> joined = new ArrayList<>(names);
            joined.addAll(path.names);
            return new MemoryPath(fileSystem, root, joined);
        }
        return path;
    }

    private static @Nullable String driveOf(String root) {
        return root.length() >= 2 && root.charAt(1) == ':' ? root.substring(0, 2) : null;
    }

    @Override
    public MemoryPath relativize(Path other) {
        MemoryPath path = check(other);
        if (!sameRoot(path)) {
            throw new IllegalArgumentException("'other' has a different root: " + path + " from " + this);
        }
        List<String> from = isEmpty() ? List.of() : names;
        List<String> to = path.isEmpty() ? List.of() : path.names;
        int common = 0;
        while (common < from.size() && common < to.size() && sameName(from.get(common), to.get(common))) {
            common++;
        }
        List<String> relative = new ArrayList<>();
        for (int i = common; i < from.size(); i++) {
            relative.add("..");
        }
        relative.addAll(to.subList(common, to.size()));
        if (relative.isEmpty()) {
            relative.add("");
        }
        return new MemoryPath(fileSystem, null, relative);
    }

    @Override
    public URI toUri() {
        return fileSystem.toUri(toAbsolutePath());
    }

    @Override
    public MemoryPath toAbsolutePath() {
        fileSystem.checkOpen();
        return isAbsolute() ? this : fileSystem.workingDirectory().resolve(this);
    }

    @Override
    public MemoryPath toRealPath(LinkOption... options) throws IOException {
        return fileSystem.operations().toRealPath(this, Options.followLinks(options));
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
            throws IOException {
        requireNonNull(watcher, "watcher");
        if (!(watcher instanceof PollingWatchService polling) || polling.fileSystem() != fileSystem) {
            throw new ProviderMismatchException("Not a watch service of this file system: " + watcher);
        }
        return polling.register(this, events, modifiers);
    }

    @Override
    public int compareTo(Path other) {
        MemoryPath path = (MemoryPath) other;
        return ignoresCase()
                ? toString().compareToIgnoreCase(path.toString())
                : toString().compareTo(path.toString());
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof MemoryPath path && path.fileSystem == fileSystem && compareTo(path) == 0;
    }

    @Override
    public int hashCode() {
        if (!ignoresCase()) {
            return toString().hashCode();
        }
        // Folds each letter as compareToIgnoreCase compares it, so that paths that are equal hash the same.
        return toString()
                .codePoints()
                .map(c -> Character.toLowerCase(Character.toUpperCase(c)))
                .reduce(0, (hash, c) -> 31 * hash + c);
    }

    @Override
    public String toString() {
        String written = string;
        if (written == null) {
            written = fileSystem.syntax().toString(root, names);
            string = written;
        }
        return written;
    }

    private boolean ignoresCase() {
        return fileSystem.configuration().style() == PathStyle.WINDOWS;
    }

    private boolean sameRoot(MemoryPath other) {
        if (root == null || other.root == null) {
            return root == null && other.root == null;
        }
        return sameName(root.replace('/', '\\'), other.root.replace('/', '\\'));
    }

    private boolean sameNames(List<String> left, List<String> right) {
        for (int i = 0; i < left.size(); i++) {
            if (!sameName(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean sameName(String left, String right) {
        return ignoresCase() ? left.equalsIgnoreCase(right) : left.equals(right);
    }

    private MemoryPath check(Path other) {
        requireNonNull(other, "other");
        if (!(other instanceof MemoryPath path) || path.fileSystem != fileSystem) {
            throw new ProviderMismatchException("Not a path of " + fileSystem + ": " + other);
        }
        return path;
    }
}
