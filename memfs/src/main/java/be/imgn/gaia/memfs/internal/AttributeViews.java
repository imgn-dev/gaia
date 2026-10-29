package be.imgn.gaia.memfs.internal;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystemException;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** The attribute views of the files of a file system. */
final class AttributeViews {

    private AttributeViews() {}

    // The view of a type, or null if the file system does not support it.
    static <V extends FileAttributeView> @Nullable V view(MemoryFileSystem fileSystem, Locator locator, Class<V> type) {
        String name = MemoryFileStore.viewName(type);
        if (name == null || !fileSystem.attributes().supports(name)) {
            return null;
        }
        FileAttributeView view = switch (name) {
            case Views.OWNER -> new Owner(fileSystem, locator);
            case Views.POSIX -> new Posix(fileSystem, locator);
            case Views.DOS -> new Dos(fileSystem, locator);
            case Views.ACL -> new Acl(fileSystem, locator);
            case Views.USER -> new User(fileSystem, locator);
            default -> new Basic(fileSystem, locator);
        };
        return type.cast(view);
    }

    /** A view: what it reads and writes, under the lock of the tree. */
    private abstract static class View {

        private final MemoryFileSystem fileSystem;
        private final Locator locator;

        View(MemoryFileSystem fileSystem, Locator locator) {
            this.fileSystem = fileSystem;
            this.locator = locator;
        }

        final MemoryFileSystem fileSystem() {
            return fileSystem;
        }

        final <T> T read(Reading<T> reading) throws IOException {
            return fileSystem.operations().reading(() -> reading.read(locator.node()));
        }

        final void write(Writing writing) throws IOException {
            fileSystem.operations().writing(() -> {
                Node node = locator.node();
                writing.write(node, locator.path());
                node.changed(fileSystem.now());
                return node;
            });
        }

        final void writeOwned(String attribute, Object value) throws IOException {
            write((node, path) -> {
                fileSystem.access().checkOwner(node, path);
                fileSystem.attributes().set(node, attribute, value, false);
            });
        }

        final UserPrincipal owner() throws IOException {
            return read(Attributes::owner);
        }

        final void owner(UserPrincipal owner) throws IOException {
            writeOwned("owner:owner", requireNonNull(owner, "owner"));
        }

        /** Reads from a file. */
        @FunctionalInterface
        interface Reading<T> {

            T read(Node node) throws IOException;
        }

        /** Writes to a file. */
        @FunctionalInterface
        interface Writing {

            void write(Node node, MemoryPath path) throws IOException;
        }
    }

    /** The {@code basic} view. */
    private static class Basic extends View implements BasicFileAttributeView {

        Basic(MemoryFileSystem fileSystem, Locator locator) {
            super(fileSystem, locator);
        }

        @Override
        public String name() {
            return Views.BASIC;
        }

        @Override
        public BasicFileAttributes readAttributes() throws IOException {
            return read(node -> fileSystem().attributes().basic(node));
        }

        @Override
        public void setTimes(
                @Nullable FileTime lastModifiedTime, @Nullable FileTime lastAccessTime, @Nullable FileTime createTime)
                throws IOException {
            write((node, path) -> {
                fileSystem().access().checkOwner(node, path);
                node.setTimes(
                        lastModifiedTime,
                        lastAccessTime,
                        createTime,
                        fileSystem().now());
            });
        }
    }

    /** The {@code owner} view. */
    private static final class Owner extends View implements FileOwnerAttributeView {

        Owner(MemoryFileSystem fileSystem, Locator locator) {
            super(fileSystem, locator);
        }

        @Override
        public String name() {
            return Views.OWNER;
        }

        @Override
        public UserPrincipal getOwner() throws IOException {
            return owner();
        }

        @Override
        public void setOwner(UserPrincipal owner) throws IOException {
            owner(owner);
        }
    }

    /** The {@code posix} view. */
    private static final class Posix extends Basic implements PosixFileAttributeView {

        Posix(MemoryFileSystem fileSystem, Locator locator) {
            super(fileSystem, locator);
        }

        @Override
        public String name() {
            return Views.POSIX;
        }

        @Override
        public PosixFileAttributes readAttributes() throws IOException {
            return read(node -> fileSystem().attributes().posix(node));
        }

        @Override
        public void setPermissions(Set<PosixFilePermission> permissions) throws IOException {
            writeOwned("posix:permissions", Set.copyOf(requireNonNull(permissions, "permissions")));
        }

        @Override
        public void setGroup(GroupPrincipal group) throws IOException {
            writeOwned("posix:group", requireNonNull(group, "group"));
        }

        @Override
        public UserPrincipal getOwner() throws IOException {
            return owner();
        }

        @Override
        public void setOwner(UserPrincipal owner) throws IOException {
            owner(owner);
        }
    }

    /** The {@code dos} view. */
    private static final class Dos extends Basic implements DosFileAttributeView {

        Dos(MemoryFileSystem fileSystem, Locator locator) {
            super(fileSystem, locator);
        }

        @Override
        public String name() {
            return Views.DOS;
        }

        @Override
        public DosFileAttributes readAttributes() throws IOException {
            return read(node -> fileSystem().attributes().dos(node));
        }

        @Override
        public void setReadOnly(boolean value) throws IOException {
            flag("readonly", value);
        }

        @Override
        public void setHidden(boolean value) throws IOException {
            flag("hidden", value);
        }

        @Override
        public void setSystem(boolean value) throws IOException {
            flag("system", value);
        }

        @Override
        public void setArchive(boolean value) throws IOException {
            flag("archive", value);
        }

        private void flag(String name, boolean value) throws IOException {
            write((node, path) -> {
                fileSystem().access().checkAttributesWritable(node, path);
                fileSystem().attributes().set(node, "dos:" + name, value, false);
            });
        }
    }

    /** The {@code acl} view. */
    private static final class Acl extends View implements AclFileAttributeView {

        Acl(MemoryFileSystem fileSystem, Locator locator) {
            super(fileSystem, locator);
        }

        @Override
        public String name() {
            return Views.ACL;
        }

        @Override
        public List<AclEntry> getAcl() throws IOException {
            return read(node -> {
                List<?> acl = (List<?>) requireNonNull(node.attribute("acl:acl"));
                return acl.stream().map(AclEntry.class::cast).toList();
            });
        }

        @Override
        public void setAcl(List<AclEntry> acl) throws IOException {
            writeOwned("acl:acl", List.copyOf(requireNonNull(acl, "acl")));
        }

        @Override
        public UserPrincipal getOwner() throws IOException {
            return owner();
        }

        @Override
        public void setOwner(UserPrincipal owner) throws IOException {
            owner(owner);
        }
    }

    /** The {@code user} view: attributes of any name, whose values are bytes. */
    private static final class User extends View implements UserDefinedFileAttributeView {

        User(MemoryFileSystem fileSystem, Locator locator) {
            super(fileSystem, locator);
        }

        @Override
        public String name() {
            return Views.USER;
        }

        @Override
        public List<String> list() throws IOException {
            return read(node -> List.copyOf(node.userAttributeNames()));
        }

        @Override
        public int size(String name) throws IOException {
            return value(requireNonNull(name, "name")).length;
        }

        @Override
        public int read(String name, ByteBuffer dst) throws IOException {
            requireNonNull(dst, "dst");
            byte[] value = value(requireNonNull(name, "name"));
            if (dst.remaining() < value.length) {
                throw new FileSystemException(null, null, "Buffer too small for attribute " + name);
            }
            dst.put(value);
            return value.length;
        }

        @Override
        public int write(String name, ByteBuffer src) throws IOException {
            requireNonNull(name, "name");
            int length = requireNonNull(src, "src").remaining();
            write((node, path) -> {
                fileSystem().access().checkAttributesWritable(node, path);
                fileSystem().attributes().set(node, "user:" + name, src, false);
            });
            return length;
        }

        @Override
        public void delete(String name) throws IOException {
            requireNonNull(name, "name");
            write((node, path) -> {
                fileSystem().access().checkAttributesWritable(node, path);
                node.removeAttribute("user:" + name);
            });
        }

        private byte[] value(String name) throws IOException {
            return read(node -> {
                if (!(node.attribute("user:" + name) instanceof byte[] value)) {
                    throw new FileSystemException(null, null, "No attribute " + name);
                }
                return value;
            });
        }
    }
}
