package be.imgn.gaia.memfs.internal;

import be.imgn.gaia.memfs.Configuration;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Checks permissions against the current user of the configuration, if there is one: POSIX permissions, the DOS
 * read-only flag, and a non-empty access control list. Without a current user, or for {@code root}, nothing is denied.
 */
final class AccessControl {

    private static final String ROOT = "root";
    private static final String EVERYONE = "EVERYONE@";

    private final @Nullable String user;
    private final Set<String> groups;

    AccessControl(Configuration configuration) {
        this.user = configuration.currentUser().orElse(null);
        this.groups = Set.copyOf(configuration.currentGroups());
    }

    void checkSearch(Directory directory, MemoryPath path) throws AccessDeniedException {
        check(directory, path, AccessMode.EXECUTE);
    }

    void check(Node node, MemoryPath path, AccessMode... modes) throws AccessDeniedException {
        if (user == null || user.equals(ROOT)) {
            return;
        }
        for (AccessMode mode : modes) {
            if (!allowed(node, mode)) {
                throw new AccessDeniedException(path.toString());
            }
        }
    }

    // A read-only file cannot be deleted either, as on Windows.
    void checkNotReadOnly(Node node, MemoryPath path) throws AccessDeniedException {
        if (user != null && !user.equals(ROOT) && Boolean.TRUE.equals(node.attribute("dos:readonly"))) {
            throw new AccessDeniedException(path.toString());
        }
    }

    // Changing the owner, group, permissions or access control list of a file is for its owner.
    void checkOwner(Node node, MemoryPath path) throws AccessDeniedException {
        if (user != null
                && !user.equals(ROOT)
                && !name(node.attribute("owner:owner")).equals(user)) {
            throw new AccessDeniedException(path.toString());
        }
    }

    private boolean allowed(Node node, AccessMode mode) {
        if (mode == AccessMode.WRITE && Boolean.TRUE.equals(node.attribute("dos:readonly"))) {
            return false;
        }
        return posixAllows(node, mode) && aclAllows(node, mode);
    }

    private boolean posixAllows(Node node, AccessMode mode) {
        if (!(node.attribute("posix:permissions") instanceof Set<?> permissions)) {
            return true;
        }
        String owner = name(node.attribute("owner:owner"));
        String group = name(node.attribute("posix:group"));
        String clazz = owner.equals(user) ? "OWNER" : groups.contains(group) ? "GROUP" : "OTHERS";
        return permissions.contains(PosixFilePermission.valueOf(clazz + "_" + bit(mode)));
    }

    private static String bit(AccessMode mode) {
        return switch (mode) {
            case READ -> "READ";
            case WRITE -> "WRITE";
            case EXECUTE -> "EXECUTE";
        };
    }

    private boolean aclAllows(Node node, AccessMode mode) {
        if (!(node.attribute("acl:acl") instanceof List<?> acl) || acl.isEmpty()) {
            return true;
        }
        AclEntryPermission permission = switch (mode) {
            case READ -> AclEntryPermission.READ_DATA;
            case WRITE -> AclEntryPermission.WRITE_DATA;
            case EXECUTE -> AclEntryPermission.EXECUTE;
        };
        for (Object element : acl) {
            AclEntry entry = (AclEntry) element;
            if (applies(entry.principal()) && entry.permissions().contains(permission)) {
                return entry.type() == AclEntryType.ALLOW;
            }
        }
        return false;
    }

    private boolean applies(UserPrincipal principal) {
        String name = principal.getName();
        return name.equals(user) || groups.contains(name) || name.equals(EVERYONE);
    }

    private static String name(@Nullable Object principal) {
        return principal instanceof UserPrincipal named ? named.getName() : "";
    }
}
