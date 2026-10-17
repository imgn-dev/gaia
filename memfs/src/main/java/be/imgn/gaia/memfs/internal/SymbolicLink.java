package be.imgn.gaia.memfs.internal;

import java.nio.file.attribute.FileTime;

/** A symbolic link, to a path that may not exist. */
final class SymbolicLink extends Node {

    private final MemoryPath target;

    SymbolicLink(long id, FileTime now, MemoryPath target) {
        super(id, now);
        this.target = target;
    }

    MemoryPath target() {
        return target;
    }

    @Override
    long size() {
        return target.toString().length();
    }
}
