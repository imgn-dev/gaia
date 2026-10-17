package be.imgn.gaia.memfs;

/**
 * An optional capability of a file system. An operation that needs a capability the file system lacks throws
 * {@link UnsupportedOperationException}.
 *
 * @since 0.1.1
 */
public enum Feature {

    /** Hard links: {@link java.nio.file.Files#createLink}. */
    LINKS,

    /** Symbolic links: {@link java.nio.file.Files#createSymbolicLink}. */
    SYMBOLIC_LINKS,

    /**
     * {@link java.nio.file.SecureDirectoryStream}s: a {@link java.nio.file.DirectoryStream} of the file system
     * implements it.
     */
    SECURE_DIRECTORY_STREAM,

    /**
     * {@link java.nio.channels.FileChannel}s: {@link java.nio.channels.FileChannel#open} and
     * {@link java.nio.channels.AsynchronousFileChannel#open}. Without it, files are still read and written through
     * {@link java.nio.channels.SeekableByteChannel}s and streams.
     */
    FILE_CHANNEL
}
