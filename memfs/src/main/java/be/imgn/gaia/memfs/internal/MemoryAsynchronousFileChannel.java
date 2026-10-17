package be.imgn.gaia.memfs.internal;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** An asynchronous channel to a regular file: each operation runs on the executor of the channel. */
final class MemoryAsynchronousFileChannel extends AsynchronousFileChannel {

    private final MemoryFileChannel channel;
    private final ExecutorService executor;

    MemoryAsynchronousFileChannel(MemoryFileChannel channel, ExecutorService executor) {
        this.channel = channel;
        this.executor = executor;
    }

    @Override
    public long size() throws IOException {
        return channel.size();
    }

    @Override
    public AsynchronousFileChannel truncate(long size) throws IOException {
        channel.truncate(size);
        return this;
    }

    @Override
    public void force(boolean metaData) throws IOException {
        channel.force(metaData);
    }

    @Override
    public <A> void lock(
            long position, long size, boolean shared, A attachment, CompletionHandler<FileLock, ? super A> handler) {
        requireNonNull(handler, "handler");
        complete(run(() -> tryLock(position, size, shared)), attachment, handler);
    }

    @Override
    public Future<FileLock> lock(long position, long size, boolean shared) {
        return run(() -> tryLock(position, size, shared));
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        if (position < 0 || size < 0 || position + size < 0) {
            throw new IllegalArgumentException("Invalid region: " + position + ", " + size);
        }
        channel.ensureOpen();
        if (shared && !channel.isReadable()) {
            throw new NonReadableChannelException();
        }
        if (!shared && !channel.isWritable()) {
            throw new NonWritableChannelException();
        }
        return channel.file().lock(new MemoryFileLock(this, channel.file(), position, size, shared));
    }

    @Override
    public <A> void read(ByteBuffer dst, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
        requireNonNull(handler, "handler");
        complete(startRead(dst, position), attachment, handler);
    }

    @Override
    public Future<Integer> read(ByteBuffer dst, long position) {
        return startRead(dst, position);
    }

    private CompletableFuture<Integer> startRead(ByteBuffer dst, long position) {
        requireNonNull(dst, "dst");
        if (dst.isReadOnly()) {
            throw new IllegalArgumentException("Read-only buffer");
        }
        checkPosition(position);
        return run(() -> channel.read(dst, position));
    }

    @Override
    public <A> void write(ByteBuffer src, long position, A attachment, CompletionHandler<Integer, ? super A> handler) {
        requireNonNull(handler, "handler");
        complete(startWrite(src, position), attachment, handler);
    }

    @Override
    public Future<Integer> write(ByteBuffer src, long position) {
        return startWrite(src, position);
    }

    private CompletableFuture<Integer> startWrite(ByteBuffer src, long position) {
        requireNonNull(src, "src");
        checkPosition(position);
        return run(() -> channel.write(src, position));
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        channel.close();
        channel.file().releaseLocks(this);
    }

    private static void checkPosition(long position) {
        if (position < 0) {
            throw new IllegalArgumentException("Negative position: " + position);
        }
    }

    private <T> CompletableFuture<T> run(FileOperations.Call<T> call) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return call.call();
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                },
                executor);
    }

    private static <T, A> void complete(
            CompletableFuture<T> future, A attachment, CompletionHandler<T, ? super A> handler) {
        CompletableFuture<T> unused = future.whenComplete((result, failure) -> {
            if (failure == null) {
                handler.completed(result, attachment);
            } else {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
                handler.failed(cause, attachment);
            }
        });
    }
}
