package be.imgn.gaia.memfs.internal;

import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A directory: its entries, by the key of their names, and the directory it is in. */
final class Directory extends Node {

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private Directory parent;
    private String name;

    // A root: its own parent, named by its root.
    Directory(long id, FileTime now, String root) {
        super(id, now);
        this.parent = this;
        this.name = root;
    }

    Directory(long id, FileTime now, Directory parent, String name) {
        super(id, now);
        this.parent = parent;
        this.name = name;
    }

    @Override
    long size() {
        return 0;
    }

    synchronized Directory parent() {
        return parent;
    }

    synchronized String name() {
        return name;
    }

    synchronized boolean isRoot() {
        return parent == this;
    }

    synchronized void moveTo(Directory newParent, String newName) {
        parent = newParent;
        name = newName;
    }

    synchronized @Nullable Entry get(String key) {
        return entries.get(key);
    }

    synchronized void put(String key, String entryName, Node node) {
        entries.put(key, new Entry(entryName, node));
    }

    synchronized @Nullable Entry remove(String key) {
        return entries.remove(key);
    }

    synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    synchronized List<Entry> entries() {
        return new ArrayList<>(entries.values());
    }

    // Whether this directory is the given one or is in it, at any depth.
    boolean isIn(Directory ancestor) {
        Directory current = this;
        while (current != ancestor) {
            if (current.isRoot()) {
                return false;
            }
            current = current.parent();
        }
        return true;
    }

    /** An entry: the name as it was created, and the file it names. */
    record Entry(String name, Node node) {}
}
