package be.imgn.gaia.memfs.internal;

import be.imgn.gaia.memfs.PathStyle;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** How the paths of a {@link PathStyle} are parsed and written. */
public abstract sealed class Syntax {

    private static final Syntax UNIX = new Unix();
    private static final Syntax WINDOWS = new Windows();

    private Syntax() {}

    /**
     * Returns the syntax of a style.
     *
     * @param style the style
     * @return its syntax
     */
    public static Syntax of(PathStyle style) {
        return style == PathStyle.UNIX ? UNIX : WINDOWS;
    }

    /**
     * Parses a path.
     *
     * @param path the path
     * @return its root and names; the empty path has one empty name
     * @throws InvalidPathException if the path has a character a name cannot have
     */
    public abstract Parsed parse(String path);

    /**
     * Returns the separator of names.
     *
     * @return the separator
     */
    public abstract String separator();

    /**
     * Returns whether a root makes a path absolute: {@code C:} and {@code \} do not.
     *
     * @param root the root, as {@link #parse(String)} gives it
     * @return whether a path with this root is absolute
     */
    public abstract boolean isAbsolute(String root);

    /**
     * Returns the key that identifies a root, the same for roots written differently, as {@code c:\} and {@code C:/}.
     *
     * @param root the root, as {@link #parse(String)} gives it
     * @return the key
     */
    public abstract String rootKey(String root);

    /**
     * Checks a root given in a configuration and returns it as paths write it.
     *
     * @param root the root
     * @return the root, as paths write it
     * @throws IllegalArgumentException if it is not the root of an absolute path
     */
    public String checkRoot(String root) {
        Parsed parsed;
        try {
            parsed = parse(root);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Not a root: " + root, e);
        }
        String parsedRoot = parsed.root();
        if (parsedRoot == null || !isAbsolute(parsedRoot) || !parsed.names().isEmpty()) {
            throw new IllegalArgumentException("Not the root of an absolute path: " + root);
        }
        return parsedRoot;
    }

    /**
     * Writes a path.
     *
     * @param root the root, or {@code null}
     * @param names the names
     * @return the path
     */
    public String toString(@Nullable String root, List<String> names) {
        String joined = String.join(separator(), names);
        return root == null ? joined : root + joined;
    }

    /**
     * Writes the path of a {@code mem:} URI: names separated by {@code /}, after a {@code /}.
     *
     * @param root the root of an absolute path
     * @param names the names
     * @return the path of the URI
     */
    public abstract String toUriPath(String root, List<String> names);

    /**
     * Reads the path of a {@code mem:} URI.
     *
     * @param uriPath the path of the URI, decoded
     * @return the path, as {@link #parse(String)} reads it
     */
    public abstract String fromUriPath(String uriPath);

    /**
     * A path parsed.
     *
     * @param root its root, or {@code null}
     * @param names its names
     */
    public record Parsed(@Nullable String root, List<String> names) {}

    private static final class Unix extends Syntax {

        @Override
        public Parsed parse(String path) {
            int nul = path.indexOf('\0');
            if (nul >= 0) {
                throw new InvalidPathException(path, "Nul character not allowed", nul);
            }
            if (path.isEmpty()) {
                return new Parsed(null, List.of(""));
            }
            String root = path.startsWith("/") ? "/" : null;
            List<String> names = new ArrayList<>();
            for (String name : path.split("/", -1)) {
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
            return new Parsed(root, List.copyOf(names));
        }

        @Override
        public String separator() {
            return "/";
        }

        @Override
        public boolean isAbsolute(String root) {
            return true;
        }

        @Override
        public String rootKey(String root) {
            return root;
        }

        @Override
        public String toUriPath(String root, List<String> names) {
            return "/" + String.join("/", names);
        }

        @Override
        public String fromUriPath(String uriPath) {
            return uriPath;
        }
    }

    private static final class Windows extends Syntax {

        private static final String RESERVED = "<>:\"|?*";
        private static final char LAST_CONTROL = 0x1f;
        private static final int DRIVE_ROOT_LENGTH = 3;

        @Override
        public Parsed parse(String input) {
            String path = input.replace('/', '\\');
            if (path.isEmpty()) {
                return new Parsed(null, List.of(""));
            }
            String root = root(input, path);
            List<String> names = new ArrayList<>();
            int start = root == null ? 0 : Math.min(root.length(), path.length());
            for (String name : path.substring(start).split("\\\\", -1)) {
                if (!name.isEmpty()) {
                    names.add(checkName(input, name));
                }
            }
            return new Parsed(root, List.copyOf(names));
        }

        private static @Nullable String root(String input, String path) {
            if (path.startsWith("\\\\")) {
                return uncRoot(input, path);
            }
            if (path.length() >= 2 && path.charAt(1) == ':' && isDriveLetter(path.charAt(0))) {
                return path.length() > 2 && path.charAt(2) == '\\'
                        ? path.substring(0, DRIVE_ROOT_LENGTH)
                        : path.substring(0, 2);
            }
            return path.startsWith("\\") ? "\\" : null;
        }

        private static String uncRoot(String input, String path) {
            int serverEnd = path.indexOf('\\', 2);
            if (serverEnd == 2) {
                throw new InvalidPathException(input, "UNC path is missing hostname");
            }
            if (serverEnd < 0) {
                throw new InvalidPathException(input, "UNC path is missing sharename");
            }
            int shareEnd = path.indexOf('\\', serverEnd + 1);
            int end = shareEnd < 0 ? path.length() : shareEnd;
            if (end == serverEnd + 1) {
                throw new InvalidPathException(input, "UNC path is missing sharename");
            }
            checkName(input, path.substring(2, serverEnd));
            checkName(input, path.substring(serverEnd + 1, end));
            return path.substring(0, end) + "\\";
        }

        private static boolean isDriveLetter(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }

        private static String checkName(String input, String name) {
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c <= LAST_CONTROL || RESERVED.indexOf(c) >= 0) {
                    throw new InvalidPathException(input, "Illegal char <" + c + ">", input.indexOf(c));
                }
            }
            if (name.endsWith(" ")) {
                throw new InvalidPathException(input, "Trailing char < >", input.indexOf(name) + name.length() - 1);
            }
            return name;
        }

        @Override
        public String separator() {
            return "\\";
        }

        @Override
        public boolean isAbsolute(String root) {
            return root.length() > 2 && root.endsWith("\\");
        }

        @Override
        public String rootKey(String root) {
            return root.toUpperCase(Locale.ROOT);
        }

        @Override
        public String toUriPath(String root, List<String> names) {
            // "/C:/dir" for a drive; "///server/share/dir" for a UNC share.
            return "/" + root.replace('\\', '/') + String.join("/", names);
        }

        @Override
        public String fromUriPath(String uriPath) {
            return uriPath.substring(1);
        }
    }
}
