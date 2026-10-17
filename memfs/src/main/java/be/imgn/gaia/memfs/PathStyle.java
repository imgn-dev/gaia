package be.imgn.gaia.memfs;

/**
 * How the paths of a file system are written: their separators, roots and the characters their names may have.
 *
 * @since 0.1.1
 */
public enum PathStyle {

    /**
     * As on Linux and macOS: {@code /} is the one root and the separator, and a name may have any character but
     * {@code /} and {@code NUL}.
     */
    UNIX,

    /**
     * As on Windows: {@code \} is the separator, and {@code /} is read as one too. A root is a drive, as in
     * {@code C:\}, or a UNC share, as in {@code \\server\share\}. A name may not have the characters {@code < > : " | ?
     * *}, control characters, or end with a space. {@code C:} and {@code \} start paths relative to the current
     * directory of a drive, and to the root of the current drive.
     */
    WINDOWS
}
