package be.imgn.gaia.memfs.internal;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.ClosedDirectoryStreamException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A directory stream that also works on the files of its directory, by paths relative to it, wherever the directory is
 * moved. An absolute path ignores the directory.
 */
final class MemorySecureDirectoryStream extends MemoryDirectoryStream implements SecureDirectoryStream<Path> {

    MemorySecureDirectoryStream(
            MemoryFileSystem fileSystem, Directory directory, MemoryPath path, Filter<? super Path> filter) {
        super(fileSystem, directory, path, filter);
    }

    @Override
    public SecureDirectoryStream<Path> newDirectoryStream(Path path, LinkOption... options) throws IOException {
        return (SecureDirectoryStream<Path>) fileSystem()
                .operations()
                .newDirectoryStream(directory(), check(path), entry -> true, Options.followLinks(options));
    }

    @Override
    public SeekableByteChannel newByteChannel(
            Path path, Set<? extends OpenOption> options, FileAttribute<?>... attributes) throws IOException {
        return fileSystem().operations().open(directory(), check(path), options, attributes);
    }

    @Override
    public void deleteFile(Path path) throws IOException {
        fileSystem().operations().delete(directory(), check(path), FileOperations.Deletion.FILE);
    }

    @Override
    public void deleteDirectory(Path path) throws IOException {
        fileSystem().operations().delete(directory(), check(path), FileOperations.Deletion.DIRECTORY);
    }

    @Override
    public void move(Path srcpath, SecureDirectoryStream<Path> targetdir, Path targetpath) throws IOException {
        requireNonNull(targetdir, "targetdir");
        if (!(targetdir instanceof MemorySecureDirectoryStream target) || target.fileSystem() != fileSystem()) {
            throw new ProviderMismatchException("Not a directory stream of " + fileSystem());
        }
        MemoryPath source = check(srcpath);
        MemoryPath destination = target.check(targetpath);
        fileSystem()
                .operations()
                .move(
                        directory(),
                        source,
                        target.directory(),
                        destination,
                        new Options.Copy(false, false, true, false));
    }

    @Override
    public <V extends FileAttributeView> @Nullable V getFileAttributeView(Class<V> type) {
        requireNonNull(type, "type");
        checkOpen();
        Directory directory = directory();
        return AttributeViews.view(
                fileSystem(), Locator.of(directory, fileSystem().tree().pathOf(directory)), type);
    }

    @Override
    public <V extends FileAttributeView> @Nullable V getFileAttributeView(
            Path path, Class<V> type, LinkOption... options) {
        requireNonNull(type, "type");
        MemoryPath checked = check(path);
        return AttributeViews.view(
                fileSystem(), Locator.of(fileSystem(), directory(), checked, Options.followLinks(options)), type);
    }

    private MemoryPath check(Path path) {
        requireNonNull(path, "path");
        checkOpen();
        if (!(path instanceof MemoryPath memoryPath) || memoryPath.getFileSystem() != fileSystem()) {
            throw new ProviderMismatchException("Not a path of " + fileSystem() + ": " + path);
        }
        return memoryPath;
    }

    private void checkOpen() {
        if (!isOpen()) {
            throw new ClosedDirectoryStreamException();
        }
    }
}
