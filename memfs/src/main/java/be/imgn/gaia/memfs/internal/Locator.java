package be.imgn.gaia.memfs.internal;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

/** Finds the file an attribute view works on, each time it is used: the file may come and go after the view. */
interface Locator {

    Node node() throws IOException;

    MemoryPath path();

    static Locator of(MemoryFileSystem fileSystem, @Nullable Directory base, MemoryPath path, boolean followLinks) {
        return new Locator() {
            @Override
            public Node node() throws IOException {
                return fileSystem.operations().node(base, path, followLinks);
            }

            @Override
            public MemoryPath path() {
                return path;
            }
        };
    }

    static Locator of(Node node, MemoryPath path) {
        return new Locator() {
            @Override
            public Node node() {
                return node;
            }

            @Override
            public MemoryPath path() {
                return path;
            }
        };
    }
}
