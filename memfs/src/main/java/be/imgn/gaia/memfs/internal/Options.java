package be.imgn.gaia.memfs.internal;

import static java.util.Objects.requireNonNull;

import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Set;

/** The options of an operation, checked and sorted out. */
final class Options {

    private Options() {}

    static boolean followLinks(LinkOption... options) {
        requireNonNull(options, "options");
        for (LinkOption option : options) {
            if (requireNonNull(option, "options") == LinkOption.NOFOLLOW_LINKS) {
                return false;
            }
        }
        return true;
    }

    /** The options a file is opened with. */
    record Open(Set<StandardOpenOption> options, boolean followLinks) {

        static Open of(Set<? extends OpenOption> given) {
            requireNonNull(given, "options");
            Set<StandardOpenOption> options = EnumSet.noneOf(StandardOpenOption.class);
            boolean follow = true;
            for (OpenOption option : given) {
                switch (requireNonNull(option, "options")) {
                    case StandardOpenOption standard -> options.add(standard);
                    case LinkOption link -> follow = link != LinkOption.NOFOLLOW_LINKS;
                    default -> throw new UnsupportedOperationException("Unsupported option: " + option);
                }
            }
            if (options.contains(StandardOpenOption.APPEND)) {
                options.add(StandardOpenOption.WRITE);
                if (options.contains(StandardOpenOption.READ)) {
                    throw new IllegalArgumentException("READ + APPEND not allowed");
                }
                if (options.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
                    throw new IllegalArgumentException("APPEND + TRUNCATE_EXISTING not allowed");
                }
            }
            if (!options.contains(StandardOpenOption.WRITE)) {
                options.add(StandardOpenOption.READ);
            }
            return new Open(options, follow);
        }

        boolean has(StandardOpenOption option) {
            return options.contains(option);
        }

        boolean creates() {
            return has(StandardOpenOption.WRITE)
                    && (has(StandardOpenOption.CREATE) || has(StandardOpenOption.CREATE_NEW));
        }
    }

    /** The options a file is copied or moved with. */
    record Copy(boolean replace, boolean copyAttributes, boolean atomic, boolean followLinks) {

        static Copy of(CopyOption... given) {
            requireNonNull(given, "options");
            boolean replace = false;
            boolean attributes = false;
            boolean atomic = false;
            boolean follow = true;
            for (CopyOption option : given) {
                switch (requireNonNull(option, "options")) {
                    case StandardCopyOption.REPLACE_EXISTING -> replace = true;
                    case StandardCopyOption.COPY_ATTRIBUTES -> attributes = true;
                    case StandardCopyOption.ATOMIC_MOVE -> atomic = true;
                    case LinkOption.NOFOLLOW_LINKS -> follow = false;
                    default -> throw new UnsupportedOperationException("Unsupported option: " + option);
                }
            }
            return new Copy(replace, attributes, atomic, follow);
        }
    }
}
