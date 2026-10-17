package be.imgn.gaia.memfs.internal;

import static java.util.Objects.checkFromIndexSize;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import org.jspecify.annotations.Nullable;

/**
 * A channel to a regular file. It keeps the file even after the file is deleted, until it is closed. Locks are held for
 * the whole JVM: a lock that overlaps another one throws {@link java.nio.channels.OverlappingFileLockException}.
 */
final class MemoryFileChannel extends FileChannel {

    private static final int TRANSFER_BUFFER = 8192;

    private final MemoryFileSystem fileSystem;
    private final RegularFile file;
    private final MemoryPath path;
    private final boolean readable;
    private final boolean writable;
    private final boolean append;
    private final @Nullable Runnable onClose;
    private long position;

    MemoryFileChannel(
            MemoryFileSystem fileSystem,
            RegularFile file,
            MemoryPath path,
            boolean readable,
            boolean writable,
            boolean append,
            @Nullable Runnable onClose) {
        this.fileSystem = fileSystem;
        this.file = file;
        this.path = path;
        this.readable = readable;
        this.writable = writable;
        this.append = append;
        this.onClose = onClose;
        file.opened();
        fileSystem.track(this);
    }

    RegularFile file() {
        return file;
    }

    boolean isReadable() {
        return readable;
    }

    boolean isWritable() {
        return writable;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        requireNonNull(dst, "dst");
        checkReadable();
        boolean completed = false;
        begin();
        try {
            int read;
            synchronized (this) {
                read = file.read(position, dst);
                if (read > 0) {
                    position += read;
                }
            }
            file.accessed(fileSystem.now());
            completed = true;
            return read;
        } finally {
            end(completed);
        }
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        requireNonNull(dsts, "dsts");
        checkFromIndexSize(offset, length, dsts.length);
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            int read = read(dsts[i]);
            if (read < 0) {
                return total == 0 ? -1 : total;
            }
            total += read;
            if (dsts[i].hasRemaining()) {
                break;
            }
        }
        return total;
    }

    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        requireNonNull(dst, "dst");
        if (position < 0) {
            throw new IllegalArgumentException("Negative position: " + position);
        }
        checkReadable();
        int read = file.read(position, dst);
        file.accessed(fileSystem.now());
        return read;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        requireNonNull(src, "src");
        checkWritable();
        boolean completed = false;
        begin();
        try {
            int written;
            synchronized (this) {
                written = file.write(position, src, append, path.toString());
                position = append ? file.size() : position + written;
            }
            file.modified(fileSystem.now());
            completed = true;
            return written;
        } finally {
            end(completed);
        }
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        requireNonNull(srcs, "srcs");
        checkFromIndexSize(offset, length, srcs.length);
        long total = 0;
        for (int i = offset; i < offset + length; i++) {
            total += write(srcs[i]);
        }
        return total;
    }

    @Override
    public int write(ByteBuffer src, long position) throws IOException {
        requireNonNull(src, "src");
        if (position < 0) {
            throw new IllegalArgumentException("Negative position: " + position);
        }
        checkWritable();
        int written = file.write(position, src, false, path.toString());
        file.modified(fileSystem.now());
        return written;
    }

    @Override
    public synchronized long position() throws IOException {
        ensureOpen();
        return position;
    }

    @Override
    public synchronized FileChannel position(long newPosition) throws IOException {
        if (newPosition < 0) {
            throw new IllegalArgumentException("Negative position: " + newPosition);
        }
        ensureOpen();
        position = newPosition;
        return this;
    }

    @Override
    public long size() throws IOException {
        ensureOpen();
        return file.size();
    }

    @Override
    public synchronized FileChannel truncate(long size) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("Negative size: " + size);
        }
        checkWritable();
        if (size < file.size()) {
            file.truncate(size);
            file.modified(fileSystem.now());
        }
        position = Math.min(position, size);
        return this;
    }

    @Override
    public void force(boolean metaData) throws IOException {
        ensureOpen();
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        requireNonNull(target, "target");
        checkTransfer(position, count);
        checkReadable();
        ByteBuffer buffer = ByteBuffer.allocate(TRANSFER_BUFFER);
        long transferred = 0;
        while (transferred < count) {
            buffer.clear().limit((int) Math.min(TRANSFER_BUFFER, count - transferred));
            int read = file.read(position + transferred, buffer);
            if (read <= 0) {
                break;
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                transferred += target.write(buffer);
            }
        }
        file.accessed(fileSystem.now());
        return transferred;
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        requireNonNull(src, "src");
        checkTransfer(position, count);
        checkWritable();
        if (position > file.size()) {
            return 0;
        }
        ByteBuffer buffer = ByteBuffer.allocate(TRANSFER_BUFFER);
        long transferred = 0;
        while (transferred < count) {
            buffer.clear().limit((int) Math.min(TRANSFER_BUFFER, count - transferred));
            int read = src.read(buffer);
            if (read <= 0) {
                break;
            }
            buffer.flip();
            transferred += file.write(position + transferred, buffer, false, path.toString());
        }
        file.modified(fileSystem.now());
        return transferred;
    }

    private static void checkTransfer(long position, long count) {
        if (position < 0 || count < 0) {
            throw new IllegalArgumentException("Negative position or count: " + position + ", " + count);
        }
    }

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) {
        throw new UnsupportedOperationException("Files in memory cannot be mapped");
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
        return tryLock(position, size, shared);
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        if (position < 0 || size < 0 || position + size < 0) {
            throw new IllegalArgumentException("Invalid region: " + position + ", " + size);
        }
        ensureOpen();
        if (shared && !readable) {
            throw new NonReadableChannelException();
        }
        if (!shared && !writable) {
            throw new NonWritableChannelException();
        }
        return file.lock(new MemoryFileLock(this, file, position, size, shared));
    }

    @Override
    protected void implCloseChannel() {
        fileSystem.untrack(this);
        file.releaseLocks(this);
        file.closed();
        if (onClose != null) {
            onClose.run();
        }
    }

    void ensureOpen() throws ClosedChannelException {
        if (!isOpen()) {
            throw new ClosedChannelException();
        }
    }

    private void checkReadable() throws ClosedChannelException {
        ensureOpen();
        if (!readable) {
            throw new NonReadableChannelException();
        }
    }

    private void checkWritable() throws ClosedChannelException {
        ensureOpen();
        if (!writable) {
            throw new NonWritableChannelException();
        }
    }
}
