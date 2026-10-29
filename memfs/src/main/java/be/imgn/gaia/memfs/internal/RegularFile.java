package be.imgn.gaia.memfs.internal;

import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileSystemException;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A regular file: its content, in blocks of the disk, and the locks held on it. Its blocks go back to the disk once it
 * has no link and no open channel.
 */
final class RegularFile extends Node {

    private final Disk disk;
    private final List<byte[]> blocks = new ArrayList<>();
    private final ReadWriteLock contentLock = new ReentrantReadWriteLock();
    private final List<MemoryFileLock> fileLocks = new ArrayList<>();
    private long size;
    private int openChannels;

    RegularFile(long id, FileTime now, Disk disk) {
        super(id, now);
        this.disk = disk;
    }

    @Override
    long size() {
        contentLock.readLock().lock();
        try {
            return size;
        } finally {
            contentLock.readLock().unlock();
        }
    }

    // Reads at a position, as much as fits in the buffer; -1 at the end of the file.
    int read(long position, ByteBuffer dst) {
        contentLock.readLock().lock();
        try {
            if (position >= size) {
                return -1;
            }
            int length = (int) Math.min(dst.remaining(), size - position);
            copy(position, length, dst);
            return length;
        } finally {
            contentLock.readLock().unlock();
        }
    }

    private void copy(long position, int length, ByteBuffer dst) {
        int blockSize = disk.blockSize();
        long offset = position;
        int left = length;
        while (left > 0) {
            int block = (int) (offset / blockSize);
            int within = (int) (offset % blockSize);
            int chunk = Math.min(left, blockSize - within);
            dst.put(blocks.get(block), within, chunk);
            offset += chunk;
            left -= chunk;
        }
    }

    // Writes the buffer at a position, growing the file as needed; at the end of the file if append is set.
    int write(long position, ByteBuffer src, boolean append, String path) throws FileSystemException {
        contentLock.writeLock().lock();
        try {
            long start = append ? size : position;
            int length = src.remaining();
            if (length == 0) {
                // As on a disk: writing nothing does not grow the file, wherever it writes.
                return 0;
            }
            if (start > Long.MAX_VALUE - length) {
                throw new FileSystemException(path, null, "File too large");
            }
            grow(start + length, path);
            int blockSize = disk.blockSize();
            long offset = start;
            while (src.hasRemaining()) {
                int block = (int) (offset / blockSize);
                int within = (int) (offset % blockSize);
                int chunk = Math.min(src.remaining(), blockSize - within);
                src.get(blocks.get(block), within, chunk);
                offset += chunk;
            }
            size = Math.max(size, start + length);
            return length;
        } finally {
            contentLock.writeLock().unlock();
        }
    }

    private void grow(long newSize, String path) throws FileSystemException {
        int blockSize = disk.blockSize();
        long needed = (newSize + blockSize - 1) / blockSize - blocks.size();
        if (needed > 0) {
            disk.allocate(needed, path);
            for (long i = 0; i < needed; i++) {
                blocks.add(new byte[blockSize]);
            }
        }
    }

    void truncate(long newSize) {
        contentLock.writeLock().lock();
        try {
            if (newSize >= size) {
                return;
            }
            int blockSize = disk.blockSize();
            int kept = (int) ((newSize + blockSize - 1) / blockSize);
            int freed = blocks.size() - kept;
            blocks.subList(kept, blocks.size()).clear();
            disk.free(freed);
            int within = (int) (newSize % blockSize);
            if (within > 0) {
                // A later write past the end must read back zeros, not what was truncated.
                Arrays.fill(blocks.getLast(), within, blockSize, (byte) 0);
            }
            size = newSize;
        } finally {
            contentLock.writeLock().unlock();
        }
    }

    synchronized void opened() {
        openChannels++;
    }

    synchronized void closed() {
        openChannels--;
        releaseIfUnused();
    }

    @Override
    synchronized void unlink() {
        super.unlink();
        releaseIfUnused();
    }

    private void releaseIfUnused() {
        if (links() == 0 && openChannels == 0) {
            truncate(0);
        }
    }

    // Locks a region, unless it overlaps one already locked: locks are held for the whole JVM.
    MemoryFileLock lock(MemoryFileLock lock) {
        synchronized (fileLocks) {
            for (MemoryFileLock held : fileLocks) {
                if (held.isValid() && held.overlaps(lock.position(), lock.size())) {
                    throw new OverlappingFileLockException();
                }
            }
            fileLocks.add(lock);
            return lock;
        }
    }

    void release(MemoryFileLock lock) {
        synchronized (fileLocks) {
            fileLocks.remove(lock);
        }
    }

    // Releases the locks a channel holds, as it closes.
    void releaseLocks(Channel channel) {
        List<MemoryFileLock> held;
        synchronized (fileLocks) {
            held = fileLocks.stream()
                    .filter(lock -> channel.equals(lock.acquiredBy()))
                    .toList();
        }
        held.forEach(MemoryFileLock::invalidate);
    }
}
