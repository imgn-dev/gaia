package be.imgn.gaia.memfs.internal;

import static java.util.Objects.requireNonNull;

import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The users and groups of a file system. Any name is a user or a group: it is created when it is first looked up, with
 * the next free id; {@code root} has the id 0.
 */
final class Principals extends UserPrincipalLookupService {

    private static final String ROOT = "root";
    private static final int FIRST_ID = 1000;

    private final Map<String, User> users = new HashMap<>();
    private final Map<String, Group> groups = new HashMap<>();
    private int nextUserId = FIRST_ID;
    private int nextGroupId = FIRST_ID;

    @Override
    public UserPrincipal lookupPrincipalByName(String name) {
        return user(requireNonNull(name, "name"));
    }

    @Override
    public GroupPrincipal lookupPrincipalByGroupName(String group) {
        return group(requireNonNull(group, "group"));
    }

    synchronized User user(String name) {
        return users.computeIfAbsent(name, key -> new User(key, key.equals(ROOT) ? 0 : nextUserId++));
    }

    synchronized Group group(String name) {
        return groups.computeIfAbsent(name, key -> new Group(key, key.equals(ROOT) ? 0 : nextGroupId++));
    }

    // The id of a principal: its own if it is one of a memory file system, else none.
    static int idOf(@Nullable Object principal) {
        return switch (principal) {
            case User user -> user.id();
            case Group group -> group.id();
            case null, default -> -1;
        };
    }

    /** A user. */
    record User(String name, int id) implements UserPrincipal {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** A group. */
    record Group(String name, int id) implements GroupPrincipal {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
