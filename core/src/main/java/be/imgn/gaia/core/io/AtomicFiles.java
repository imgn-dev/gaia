package be.imgn.gaia.core.io;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.base.Charsets;
import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;

/**
 * Writes files atomically: a reader sees either the previous content of a file or its new content, never a part of it,
 * and so does the next run of a program after a crash.
 *
 * <p>The content is written to a temporary file in the same directory, forced to the storage device, then moved over
 * the file in one atomic step. If anything fails, the file is left as it was and the temporary file is deleted.
 *
 * <ul>
 *   <li>A file that exists keeps its POSIX permissions, which the temporary file has from its creation, along with
 *       reading by its owner before anything is written to it; a new one gets the default permissions, as with
 *       {@link Files#write(Path, byte[], java.nio.file.OpenOption...)}. Other attributes, such as the owner, ACLs or
 *       extended attributes, are not kept.
 *   <li>A symbolic link to a file that exists is followed: the file it points to is replaced, and the link stays. A
 *       symbolic link to a file that does not exist is not followed: it is replaced with the new file.
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
    // Short enough that the name of a temporary file, at most 3 bytes per char in UTF-8, stays within the 255 bytes
    // most
    // file systems allow in a name.
    private static final int MAX_EMBEDDED_NAME_LENGTH = 64;

    private AtomicFiles() {}

    /**
     * Writes bytes to a file atomically, creating it or replacing its content.
     *
     * @param file the file to write
     * @param bytes the new content of the file
     * @throws IOException if the file cannot be written, which leaves it as it was
     */
    public static void write(Path file, byte[] bytes) throws IOException {
        requireNonNull(file, "file");
        requireNonNull(bytes, "bytes");
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
        requireNonNull(file, "file");
        requireNonNull(output, "output");
        Path target = Files.exists(file) ? file.toRealPath() : file.toAbsolutePath();
        Path directory = target.getParent();
        if (directory == null || Files.isDirectory(target)) {
            throw new FileSystemException(target.toString(), null, "Is a directory");
        }
        Set<PosixFilePermission> permissions = permissionsToKeep(target);
        Temporary temporary = createTemporary(directory, target, permissions);
        var moved = false;
        try {
            try (FileChannel channel = temporary.channel()) {
                restorePermissions(temporary.path(), permissions);
                writeDurably(channel, output);
            }
            Files.move(temporary.path(), target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) {
                deleteQuietly(temporary.path());
            }
        }
        forceDirectory(directory);
    }

    /**
     * Writes text to a file atomically in the {@linkplain Charsets#DEFAULT_CHARSET current default charset}, creating
     * it or replacing its content.
     *
     * @param file the file to write
     * @param text the new content of the file
     * @throws IOException if the file cannot be written, which leaves it as it was
     * @throws java.nio.charset.CharacterCodingException if the text contains a character that charset cannot encode, or
     *     a malformed surrogate pair
     */
    public static void writeString(Path file, CharSequence text) throws IOException {
        requireNonNull(file, "file");
        requireNonNull(text, "text");
        writeString(file, text, Charsets.DEFAULT_CHARSET);
    }

    /**
     * Writes text to a file atomically in a charset, creating it or replacing its content.
     *
     * @param file the file to write
     * @param text the new content of the file
     * @param charset the charset to encode the text in
     * @throws IOException if the file cannot be written, which leaves it as it was
     * @throws IllegalArgumentException if {@code charset} does not support encoding
     * @throws java.nio.charset.CharacterCodingException if the text contains a character the charset cannot encode, or
     *     a malformed surrogate pair
     */
    public static void writeString(Path file, CharSequence text, Charset charset) throws IOException {
        requireNonNull(file, "file");
        requireNonNull(text, "text");
        requireNonNull(charset, "charset");
        checkArgument(charset.canEncode(), "%s does not support encoding", charset);
        ByteBuffer bytes = charset.newEncoder().encode(CharBuffer.wrap(text));
        write(file, out -> out.write(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining()));
    }

    // Null when there are none to keep: the file is new, or its file system has no POSIX permissions.
    private static @Nullable Set<PosixFilePermission> permissionsToKeep(Path target) throws IOException {
        if (Files.exists(target) && Files.getFileAttributeView(target, PosixFileAttributeView.class) != null) {
            return Files.getPosixFilePermissions(target);
        }
        return null;
    }

    // Created rather than taken from Files.createTempFile, whose files only their owner can read. Created with the
    // permissions of the file it replaces, since a stream opened on it would keep reading after they are narrowed; a
    // new file gets the default permissions, which it keeps once moved. Its owner can also read it until its
    // permissions are restored, which some JDKs do through a stream opened for reading. It is written only through the
    // channel it is created with, so that a link put in its place is never followed.
    private static Temporary createTemporary(
            Path directory, Path target, @Nullable Set<PosixFilePermission> permissions) throws IOException {
        FileAttribute<?>[] attributes;
        if (permissions == null) {
            attributes = new FileAttribute<?>[0];
        } else {
            var readable = EnumSet.of(PosixFilePermission.OWNER_READ);
            readable.addAll(permissions);
            attributes = new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(readable)};
        }
        String name = embeddedName(target);
        while (true) {
            String suffix = Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), RADIX);
            Path temporary = directory.resolve("." + name + "." + suffix + ".tmp");
            try {
                return new Temporary(
                        temporary,
                        FileChannel.open(
                                temporary,
                                Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                                attributes));
            } catch (FileAlreadyExistsException e) {
                // Another write took that name: draw another.
            }
        }
    }

    // The start of the name of the target, enough to tell which file a temporary one left behind belongs to.
    private static String embeddedName(Path target) {
        String name = String.valueOf(target.getFileName());
        if (name.length() <= MAX_EMBEDDED_NAME_LENGTH) {
            return name;
        }
        int end = Character.isHighSurrogate(name.charAt(MAX_EMBEDDED_NAME_LENGTH - 1))
                ? MAX_EMBEDDED_NAME_LENGTH - 1
                : MAX_EMBEDDED_NAME_LENGTH;
        return name.substring(0, end);
    }

    // Gives back the permissions the umask removed at creation, and removes the reading added then, without following
    // a link.
    private static void restorePermissions(Path temporary, @Nullable Set<PosixFilePermission> permissions)
            throws IOException {
        if (permissions != null
                && !Files.getPosixFilePermissions(temporary, LinkOption.NOFOLLOW_LINKS)
                        .equals(permissions)) {
            Files.setAttribute(temporary, "posix:permissions", permissions, LinkOption.NOFOLLOW_LINKS);
        }
    }

    private static void writeDurably(FileChannel channel, Output output) throws IOException {
        OutputStream out = new UncloseableOutputStream(new BufferedOutputStream(Channels.newOutputStream(channel)));
        output.writeTo(out);
        out.flush();
        channel.force(true);
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

    private record Temporary(Path path, FileChannel channel) {}

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
