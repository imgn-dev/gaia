package be.imgn.gaia.memfs.internal;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.atomic.AtomicBoolean;

/** A lock on a region of a regular file, held until it is released or its channel is closed. */
final class MemoryFileLock extends FileLock {

    private final RegularFile file;
    // Not guarded by this lock's monitor: the file checks validity while holding its own lock list.
    private final AtomicBoolean valid = new AtomicBoolean(true);

    MemoryFileLock(FileChannel channel, RegularFile file, long position, long size, boolean shared) {
        super(channel, position, size, shared);
        this.file = file;
    }

    MemoryFileLock(AsynchronousFileChannel channel, RegularFile file, long position, long size, boolean shared) {
        super(channel, position, size, shared);
        this.file = file;
    }

    @Override
    public boolean isValid() {
        return valid.get() && acquiredBy().isOpen();
    }

    @Override
    public void release() throws IOException {
        if (!acquiredBy().isOpen()) {
            throw new ClosedChannelException();
        }
        invalidate();
    }

    // Releases the lock as its channel closes, which release() refuses once the channel is closed.
    void invalidate() {
        if (valid.compareAndSet(true, false)) {
            file.release(this);
        }
    }
}
