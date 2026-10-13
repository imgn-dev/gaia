package be.imgn.gaia.core.io;

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Writes files atomically: a reader sees either the previous content of a file or its new content, never a part of it,
 * and so does the next run of a program after a crash.
 *
 * <p>The content is written to a temporary file in the same directory, forced to the storage device, then moved over
 * the file in one atomic step. If anything fails, the file is left as it was and the temporary file is deleted.
 *
 * <ul>
 *   <li>A file that exists keeps its POSIX permissions; a new one gets the default permissions, as with
 *       {@link Files#write(Path, byte[], java.nio.file.OpenOption...)}. Other attributes, such as the owner, ACLs or
 *       extended attributes, are not kept.
 *   <li>A symbolic link to a file that exists is followed: the file it points to is replaced, and the link stays.
 *   <li>On Windows, the file cannot be replaced while another program has it open: the write then fails with an
 *       {@link java.nio.file.AccessDeniedException}.
 *   <li>A file system that cannot move a file atomically within a directory makes every write fail with an
 *       {@link AtomicMoveNotSupportedException}, rather than replace the file in a way that is not atomic.
 * </ul>
 *
 * <pre>{@code
 * AtomicFiles.writeString(settingsFile, toml);
 * AtomicFiles.write(exportFile, out -> ImageIO.write(image, "png", out));
 * }</pre>
 *
 * @since 0.1.1
 */
public final class AtomicFiles {

    private static final int RADIX = 36;

    private AtomicFiles() {}

    /**
     * Writes bytes to a file atomically, creating it or replacing its content.
     *
     * @param file the file to write
     * @param bytes the new content of the file
     * @throws IOException if the file cannot be written, which leaves it as it was
     */
    public static void write(Path file, byte[] bytes) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        write(file, out -> out.write(bytes));
    }

    /**
     * Writes a file atomically, creating it or replacing its content with what {@code output} writes. The stream given
     * to {@code output} is buffered, and closing it has no effect.
     *
     * @param file the file to write
     * @param output writes the new content of the file
     * @throws FileSystemException if {@code file} is a directory
     * @throws IOException if the file cannot be written or {@code output} fails, which leaves the file as it was
     */
    public static void write(Path file, Output output) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(output, "output");
        Path target = Files.exists(file) ? file.toRealPath() : file.toAbsolutePath();
        Path directory = target.getParent();
        if (directory == null || Files.isDirectory(target)) {
            throw new FileSystemException(target.toString(), null, "Is a directory");
        }
        Path temporary = createTemporary(directory, target);
        var moved = false;
        try {
            copyPermissions(target, temporary);
            writeDurably(temporary, output);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) {
                deleteQuietly(temporary);
            }
        }
        forceDirectory(directory);
    }

    /**
     * Writes text to a file atomically in UTF-8, creating it or replacing its content.
     *
     * @param file the file to write
     * @param text the new content of the file
     * @throws IOException if the file cannot be written, which leaves it as it was
     * @throws java.nio.charset.CharacterCodingException if the text contains a malformed surrogate pair
     */
    public static void writeString(Path file, CharSequence text) throws IOException {
        writeString(file, text, StandardCharsets.UTF_8);
    }

    /**
     * Writes text to a file atomically in a charset, creating it or replacing its content.
     *
     * @param file the file to write
     * @param text the new content of the file
     * @param charset the charset to encode the text in
     * @throws IOException if the file cannot be written, which leaves it as it was
     * @throws java.nio.charset.CharacterCodingException if the text contains a character the charset cannot encode, or
     *     a malformed surrogate pair
     */
    public static void writeString(Path file, CharSequence text, Charset charset) throws IOException {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(charset, "charset");
        ByteBuffer bytes = charset.newEncoder().encode(CharBuffer.wrap(text));
        write(file, out -> out.write(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining()));
    }

    // Created rather than taken from Files.createTempFile, whose files only their owner can read.
    private static Path createTemporary(Path directory, Path target) throws IOException {
        while (true) {
            String suffix = Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), RADIX);
            Path temporary = directory.resolve("." + target.getFileName() + "." + suffix + ".tmp");
            try {
                return Files.createFile(temporary);
            } catch (FileAlreadyExistsException e) {
                // Another write took that name: draw another.
            }
        }
    }

    private static void copyPermissions(Path target, Path temporary) throws IOException {
        if (Files.exists(target) && Files.getFileAttributeView(temporary, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(target));
        }
    }

    private static void writeDurably(Path temporary, Output output) throws IOException {
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            OutputStream out = new UncloseableOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel)));
            output.writeTo(out);
            out.flush();
            channel.force(true);
        }
    }

    // Makes the new directory entry durable too. Not every system can open a directory, Windows among them: there, the
    // move is as durable as the file system makes it.
    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException e) {
            // The file is written; only its durability across a crash is left to the file system.
        }
    }

    // Called while another exception propagates, which matters more than a temporary file left behind.
    private static void deleteQuietly(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException e) {
            // The temporary file stays, hidden and harmless.
        }
    }

    /**
     * Writes the content of a file to a stream.
     *
     * @since 0.1.1
     */
    @FunctionalInterface
    public interface Output {

        /**
         * Writes the content of a file to {@code out}.
         *
         * @param out the stream to write to; closing it has no effect
         * @throws IOException if the content cannot be written
         */
        void writeTo(OutputStream out) throws IOException;
    }

    // Lets an Output close its stream, as many writers do, without closing the channel before it is forced.
    private static final class UncloseableOutputStream extends FilterOutputStream {

        UncloseableOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
