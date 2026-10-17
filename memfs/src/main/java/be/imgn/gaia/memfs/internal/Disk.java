package be.imgn.gaia.memfs.internal;

import java.nio.file.FileSystemException;

/** The blocks of a file system: how many there are, and how many files use. */
final class Disk {

    private final int blockSize;
    private final long totalBlocks;
    private long usedBlocks;

    Disk(int blockSize, long capacity) {
        this.blockSize = blockSize;
        this.totalBlocks = capacity / blockSize;
    }

    int blockSize() {
        return blockSize;
    }

    long totalSpace() {
        return totalBlocks * blockSize;
    }

    synchronized long unallocatedSpace() {
        return (totalBlocks - usedBlocks) * blockSize;
    }

    synchronized void allocate(long blocks, String file) throws FileSystemException {
        if (usedBlocks + blocks > totalBlocks) {
            throw new FileSystemException(file, null, "No space left on device");
        }
        usedBlocks += blocks;
    }

    synchronized void free(long blocks) {
        usedBlocks -= blocks;
    }
}
