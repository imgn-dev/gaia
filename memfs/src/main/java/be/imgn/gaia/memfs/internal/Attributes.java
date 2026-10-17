package be.imgn.gaia.memfs.internal;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.memfs.Configuration;
import java.nio.ByteBuffer;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** The attributes of files, by {@code view:name}: their initial values, and reading and setting them. */
final class Attributes {

    private static final List<String> BASIC = List.of(
            "lastModifiedTime",
            "lastAccessTime",
            "creationTime",
            "size",
            "isRegularFile",
            "isDirectory",
            "isSymbolicLink",
            "isOther",
            "fileKey");
    // The permissions from the highest bit of a mode to the lowest: 0400 to 0001.
    private static final List<PosixFilePermission> BITS = List.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_WRITE,
            PosixFilePermission.OTHERS_EXECUTE);
    private static final List<String> DOS = List.of("readonly", "hidden", "system", "archive");
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-r--r--");
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwxr-xr-x");
    private static final int TYPE_DIRECTORY = 0040000;
    private static final int TYPE_FILE = 0100000;
    private static final int TYPE_LINK = 0120000;
    private static final int PERMISSION_BITS = 9;

    private final MemoryFileSystem fileSystem;
    private final Configuration configuration;

    Attributes(MemoryFileSystem fileSystem) {
        this.fileSystem = fileSystem;
        this.configuration = fileSystem.configuration();
    }

    boolean supports(String view) {
        return configuration.attributeViews().contains(view);
    }

    // Gives a new file its owner, group, permissions, flags and access control list, then the attributes it is given.
    void initialize(Node node, List<FileAttribute<?>> given) {
        Map<String, Object> defaults = configuration.defaultAttributes();
        if (supports(Views.OWNER)) {
            String owner = (String) defaults.getOrDefault(
                    "owner:owner", configuration.currentUser().orElse("user"));
            node.setAttribute("owner:owner", fileSystem.principals().user(owner));
        }
        if (supports(Views.POSIX)) {
            String group = (String) defaults.getOrDefault(
                    "posix:group",
                    configuration.currentGroups().stream().findFirst().orElse("group"));
            node.setAttribute("posix:group", fileSystem.principals().group(group));
            Object permissions = defaults.getOrDefault(
                    "posix:permissions", node instanceof RegularFile ? FILE_PERMISSIONS : DIRECTORY_PERMISSIONS);
            node.setAttribute("posix:permissions", permissions);
        }
        if (supports(Views.DOS)) {
            for (String flag : DOS) {
                node.setAttribute("dos:" + flag, defaults.getOrDefault("dos:" + flag, false));
            }
        }
        if (supports(Views.ACL)) {
            node.setAttribute("acl:acl", defaults.getOrDefault("acl:acl", List.of()));
        }
        for (FileAttribute<?> attribute : given) {
            set(node, attribute.name(), attribute.value(), true);
        }
    }

    // Sets an attribute given as "view:name", or "name" for a basic one.
    void set(Node node, String attribute, Object value, boolean creating) {
        String view = viewOf(attribute);
        String name = attribute.substring(attribute.indexOf(':') + 1);
        checkView(view);
        switch (view) {
            case Views.BASIC -> setBasic(node, name, value, creating);
            case Views.OWNER, Views.ACL, Views.POSIX, Views.UNIX -> setOwned(node, view, name, value);
            case Views.DOS -> setDos(node, name, value);
            case Views.USER -> node.setAttribute(attribute, bytes(value));
            default -> throw new UnsupportedOperationException("Unsupported view: " + view);
        }
        node.changed(fileSystem.now());
    }

    private void setBasic(Node node, String name, Object value, boolean creating) {
        if (creating) {
            throw new UnsupportedOperationException("Cannot set basic:" + name + " when creating a file");
        }
        if (!name.endsWith("Time") || !BASIC.contains(name)) {
            throw new IllegalArgumentException("Cannot set basic:" + name);
        }
        FileTime time = cast(value, FileTime.class, "basic:" + name);
        FileTime now = fileSystem.now();
        switch (name) {
            case "lastModifiedTime" -> node.setTimes(time, null, null, now);
            case "lastAccessTime" -> node.setTimes(null, time, null, now);
            default -> node.setTimes(null, null, time, now);
        }
    }

    private void setOwned(Node node, String view, String name, Object value) {
        switch (view + ":" + name) {
            case "owner:owner", "posix:owner", "acl:owner" ->
                node.setAttribute("owner:owner", cast(value, UserPrincipal.class, view + ":owner"));
            case "posix:group", "unix:group" ->
                node.setAttribute("posix:group", cast(value, GroupPrincipal.class, view + ":group"));
            case "posix:permissions", "unix:permissions" -> node.setAttribute("posix:permissions", permissions(value));
            case "unix:mode" -> node.setAttribute("posix:permissions", fromMode(cast(value, Integer.class, "mode")));
            case "acl:acl" -> node.setAttribute("acl:acl", acl(value));
            default -> throw new IllegalArgumentException("Cannot set " + view + ":" + name);
        }
    }

    private static void setDos(Node node, String name, Object value) {
        if (!DOS.contains(name)) {
            throw new IllegalArgumentException("Cannot set dos:" + name);
        }
        node.setAttribute("dos:" + name, cast(value, Boolean.class, "dos:" + name));
    }

    // Reads attributes given as "view:name,name", "view:*", or without the view for basic ones.
    Map<String, Object> read(Node node, String attributes) {
        String view = viewOf(attributes);
        checkView(view);
        String names = attributes.substring(attributes.indexOf(':') + 1);
        Map<String, Object> all = all(node, view);
        if ("*".equals(names)) {
            return all;
        }
        Map<String, Object> read = new LinkedHashMap<>();
        for (String name : names.split(",", -1)) {
            Object value = all.get(name);
            if (value == null && !view.equals(Views.USER)) {
                throw new IllegalArgumentException("'" + name + "' not recognized");
            }
            if (value != null) {
                read.put(name, value);
            }
        }
        return read;
    }

    private Map<String, Object> all(Node node, String view) {
        Map<String, Object> all = new LinkedHashMap<>();
        if (!view.equals(Views.OWNER) && !view.equals(Views.ACL) && !view.equals(Views.USER)) {
            Basic basic = basic(node);
            for (String name : BASIC) {
                all.put(name, basic.get(name));
            }
        }
        switch (view) {
            case Views.OWNER, Views.ACL -> {
                all.put("owner", owner(node));
                if (view.equals(Views.ACL)) {
                    all.put("acl", node.attribute("acl:acl"));
                }
            }
            case Views.POSIX, Views.UNIX -> putPosix(node, view, all);
            case Views.DOS -> DOS.forEach(name -> all.put(name, flag(node, name)));
            case Views.USER ->
                node.userAttributeNames()
                        .forEach(name ->
                                all.put(name, ((byte[]) requireNonNull(node.attribute("user:" + name))).clone()));
            default -> {
                // basic: the attributes above
            }
        }
        return all;
    }

    private void putPosix(Node node, String view, Map<String, Object> all) {
        Posix posix = posix(node);
        all.put("permissions", posix.permissions());
        all.put("owner", posix.owner());
        all.put("group", posix.group());
        if (view.equals(Views.UNIX)) {
            all.put("dev", 0L);
            all.put("ino", node.id());
            all.put("mode", mode(node, posix.permissions()));
            all.put("nlink", node.links());
            all.put("uid", Principals.idOf(posix.owner()));
            all.put("gid", Principals.idOf(posix.group()));
            all.put("rdev", 0L);
            all.put("ctime", node.changeTime());
        }
    }

    Basic basic(Node node) {
        return new Basic(node);
    }

    Posix posix(Node node) {
        return new Posix(
                new Basic(node),
                owner(node),
                (GroupPrincipal) requireNonNull(node.attribute("posix:group")),
                permissionsOf(node));
    }

    Dos dos(Node node) {
        return new Dos(
                new Basic(node),
                flag(node, "readonly"),
                flag(node, "hidden"),
                flag(node, "system"),
                flag(node, "archive"));
    }

    static UserPrincipal owner(Node node) {
        return (UserPrincipal) requireNonNull(node.attribute("owner:owner"));
    }

    private static Set<PosixFilePermission> permissionsOf(Node node) {
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        if (node.attribute("posix:permissions") instanceof Set<?> stored) {
            for (Object permission : stored) {
                permissions.add((PosixFilePermission) permission);
            }
        }
        return Set.copyOf(permissions);
    }

    private static boolean flag(Node node, String name) {
        return Boolean.TRUE.equals(node.attribute("dos:" + name));
    }

    private static int mode(Node node, Set<PosixFilePermission> permissions) {
        int mode = 0;
        for (PosixFilePermission permission : permissions) {
            mode |= 1 << (PERMISSION_BITS - 1 - BITS.indexOf(permission));
        }
        if (node instanceof Directory) {
            return mode | TYPE_DIRECTORY;
        }
        return mode | (node instanceof RegularFile ? TYPE_FILE : TYPE_LINK);
    }

    private static Set<PosixFilePermission> fromMode(int mode) {
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        for (PosixFilePermission permission : BITS) {
            if ((mode & (1 << (PERMISSION_BITS - 1 - BITS.indexOf(permission)))) != 0) {
                permissions.add(permission);
            }
        }
        return Set.copyOf(permissions);
    }

    private static Set<PosixFilePermission> permissions(Object value) {
        Collection<?> collection = cast(value, Collection.class, "permissions");
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        for (Object element : collection) {
            permissions.add(cast(element, PosixFilePermission.class, "permissions"));
        }
        return Set.copyOf(permissions);
    }

    private static List<AclEntry> acl(Object value) {
        List<?> list = cast(value, List.class, "acl");
        return list.stream().map(entry -> cast(entry, AclEntry.class, "acl")).toList();
    }

    private static byte[] bytes(Object value) {
        return switch (value) {
            case byte[] array -> array.clone();
            case ByteBuffer buffer -> {
                byte[] array = new byte[buffer.remaining()];
                buffer.get(array);
                yield array;
            }
            default -> throw new ClassCastException("A user-defined attribute is a byte[] or a ByteBuffer: " + value);
        };
    }

    private static <T> T cast(@Nullable Object value, Class<T> type, String attribute) {
        if (!type.isInstance(value)) {
            throw new ClassCastException(attribute + " must be a " + type.getSimpleName() + ": " + value);
        }
        return type.cast(value);
    }

    private static String viewOf(String attribute) {
        int colon = attribute.indexOf(':');
        return colon < 0 ? Views.BASIC : attribute.substring(0, colon);
    }

    private void checkView(String view) {
        if (!supports(view)) {
            throw new UnsupportedOperationException("View '" + view + "' not available");
        }
    }

    /** The basic attributes of a file, as they were when they were read. */
    static final class Basic implements BasicFileAttributes {

        private final FileTime lastModifiedTime;
        private final FileTime lastAccessTime;
        private final FileTime creationTime;
        private final long size;
        private final Node node;

        Basic(Node node) {
            this.node = node;
            this.lastModifiedTime = node.lastModifiedTime();
            this.lastAccessTime = node.lastAccessTime();
            this.creationTime = node.creationTime();
            this.size = node.size();
        }

        @Nullable
        Object get(String name) {
            return switch (name) {
                case "lastModifiedTime" -> lastModifiedTime;
                case "lastAccessTime" -> lastAccessTime;
                case "creationTime" -> creationTime;
                case "size" -> size;
                case "isRegularFile" -> isRegularFile();
                case "isDirectory" -> isDirectory();
                case "isSymbolicLink" -> isSymbolicLink();
                case "isOther" -> false;
                case "fileKey" -> fileKey();
                default -> null;
            };
        }

        @Override
        public FileTime lastModifiedTime() {
            return lastModifiedTime;
        }

        @Override
        public FileTime lastAccessTime() {
            return lastAccessTime;
        }

        @Override
        public FileTime creationTime() {
            return creationTime;
        }

        @Override
        public boolean isRegularFile() {
            return node instanceof RegularFile;
        }

        @Override
        public boolean isDirectory() {
            return node instanceof Directory;
        }

        @Override
        public boolean isSymbolicLink() {
            return node instanceof SymbolicLink;
        }

        @Override
        public boolean isOther() {
            return false;
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public Object fileKey() {
            return node.id();
        }
    }

    /** The POSIX attributes of a file, as they were when they were read. */
    record Posix(Basic basic, UserPrincipal owner, GroupPrincipal group, Set<PosixFilePermission> permissions)
            implements PosixFileAttributes {

        @Override
        public FileTime lastModifiedTime() {
            return basic.lastModifiedTime();
        }

        @Override
        public FileTime lastAccessTime() {
            return basic.lastAccessTime();
        }

        @Override
        public FileTime creationTime() {
            return basic.creationTime();
        }

        @Override
        public boolean isRegularFile() {
            return basic.isRegularFile();
        }

        @Override
        public boolean isDirectory() {
            return basic.isDirectory();
        }

        @Override
        public boolean isSymbolicLink() {
            return basic.isSymbolicLink();
        }

        @Override
        public boolean isOther() {
            return false;
        }

        @Override
        public long size() {
            return basic.size();
        }

        @Override
        public Object fileKey() {
            return basic.fileKey();
        }
    }

    /** The DOS attributes of a file, as they were when they were read. */
    record Dos(Basic basic, boolean isReadOnly, boolean isHidden, boolean isSystem, boolean isArchive)
            implements DosFileAttributes {

        @Override
        public FileTime lastModifiedTime() {
            return basic.lastModifiedTime();
        }

        @Override
        public FileTime lastAccessTime() {
            return basic.lastAccessTime();
        }

        @Override
        public FileTime creationTime() {
            return basic.creationTime();
        }

        @Override
        public boolean isRegularFile() {
            return basic.isRegularFile();
        }

        @Override
        public boolean isDirectory() {
            return basic.isDirectory();
        }

        @Override
        public boolean isSymbolicLink() {
            return basic.isSymbolicLink();
        }

        @Override
        public boolean isOther() {
            return false;
        }

        @Override
        public long size() {
            return basic.size();
        }

        @Override
        public Object fileKey() {
            return basic.fileKey();
        }
    }
}
