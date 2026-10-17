package be.imgn.gaia.memfs.internal;

import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/** A lock on a region of a regular file, held until it is released or its channel is closed. */
final class MemoryFileLock extends FileLock {

    private final RegularFile file;
    private boolean valid = true;

    MemoryFileLock(FileChannel channel, RegularFile file, long position, long size, boolean shared) {
        super(channel, position, size, shared);
        this.file = file;
    }

    MemoryFileLock(AsynchronousFileChannel channel, RegularFile file, long position, long size, boolean shared) {
        super(channel, position, size, shared);
        this.file = file;
    }

    @Override
    public synchronized boolean isValid() {
        return valid && acquiredBy().isOpen();
    }

    @Override
    public synchronized void release() {
        if (valid) {
            valid = false;
            file.release(this);
        }
    }
}
