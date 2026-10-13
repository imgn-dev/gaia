package be.imgn.gaia.processing.testing.internal;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.tools.SimpleJavaFileObject;

/**
 * A file a compilation generated, held in memory and named {@code /<LOCATION>/<relative path>}, such as
 * {@code /SOURCE_OUTPUT/example/FooInfo.java}.
 */
public final class GeneratedFile extends SimpleJavaFileObject {

    private final byte[] content;

    /**
     * Holds a copy of {@code content}.
     *
     * @param path the location name and the path relative to it, separated by {@code /}
     * @param content the content of the file
     */
    public GeneratedFile(String path, byte[] content) {
        super(URI.create("mem:///" + path), kindOf(path));
        this.content = content.clone();
    }

    private static Kind kindOf(String path) {
        for (Kind kind : List.of(Kind.SOURCE, Kind.CLASS, Kind.HTML)) {
            if (path.endsWith(kind.extension)) {
                return kind;
            }
        }
        return Kind.OTHER;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return new String(content, StandardCharsets.UTF_8);
    }

    @Override
    public InputStream openInputStream() {
        return new ByteArrayInputStream(content);
    }
}
