package be.imgn.gaia.memfs.internal;

import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A file of the tree: its times, its link count and its attributes, by {@code view:name}. */
abstract sealed class Node permits Directory, RegularFile, SymbolicLink {

    private final long id;
    private final Map<String, Object> attributes = new HashMap<>();
    private FileTime creationTime;
    private FileTime lastModifiedTime;
    private FileTime lastAccessTime;
    private FileTime changeTime;
    private int links;

    Node(long id, FileTime now) {
        this.id = id;
        this.creationTime = now;
        this.lastModifiedTime = now;
        this.lastAccessTime = now;
        this.changeTime = now;
    }

    final long id() {
        return id;
    }

    abstract long size();

    final synchronized FileTime creationTime() {
        return creationTime;
    }

    final synchronized FileTime lastModifiedTime() {
        return lastModifiedTime;
    }

    final synchronized FileTime lastAccessTime() {
        return lastAccessTime;
    }

    final synchronized FileTime changeTime() {
        return changeTime;
    }

    final synchronized void setTimes(
            @Nullable FileTime modified, @Nullable FileTime accessed, @Nullable FileTime created, FileTime now) {
        if (modified != null) {
            lastModifiedTime = modified;
        }
        if (accessed != null) {
            lastAccessTime = accessed;
        }
        if (created != null) {
            creationTime = created;
        }
        changeTime = now;
    }

    final synchronized void modified(FileTime now) {
        lastModifiedTime = now;
        changeTime = now;
    }

    final synchronized void accessed(FileTime now) {
        lastAccessTime = now;
    }

    final synchronized void changed(FileTime now) {
        changeTime = now;
    }

    final synchronized int links() {
        return links;
    }

    synchronized void link() {
        links++;
    }

    synchronized void unlink() {
        links--;
    }

    final synchronized @Nullable Object attribute(String key) {
        return attributes.get(key);
    }

    final synchronized void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    final synchronized void removeAttribute(String key) {
        attributes.remove(key);
    }

    final synchronized Map<String, Object> attributes() {
        return Map.copyOf(attributes);
    }

    // The names of the user-defined attributes, stored as "user:<name>".
    final synchronized List<String> userAttributeNames() {
        List<String> names = new ArrayList<>();
        for (String key : attributes.keySet()) {
            if (key.startsWith(Views.USER + ":")) {
                names.add(key.substring(Views.USER.length() + 1));
            }
        }
        Collections.sort(names);
        return names;
    }

    final synchronized void copyAttributesFrom(Node other) {
        attributes.putAll(other.attributes());
        creationTime = other.creationTime();
        lastModifiedTime = other.lastModifiedTime();
        lastAccessTime = other.lastAccessTime();
    }
}
