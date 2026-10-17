package be.imgn.gaia.memfs.internal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The names of the attribute views, and the views each one extends. */
public final class Views {

    /** {@code basic}. */
    public static final String BASIC = "basic";

    /** {@code owner}. */
    public static final String OWNER = "owner";

    /** {@code posix}. */
    public static final String POSIX = "posix";

    /** {@code unix}. */
    public static final String UNIX = "unix";

    /** {@code dos}. */
    public static final String DOS = "dos";

    /** {@code acl}. */
    public static final String ACL = "acl";

    /** {@code user}. */
    public static final String USER = "user";

    private static final Map<String, List<String>> EXTENDED = Map.of(
            BASIC, List.of(),
            OWNER, List.of(),
            POSIX, List.of(BASIC, OWNER),
            UNIX, List.of(POSIX),
            DOS, List.of(BASIC),
            ACL, List.of(OWNER),
            USER, List.of());

    private Views() {}

    /**
     * Returns views with {@code basic} and the views they extend, as {@code posix} extends {@code basic} and
     * {@code owner}.
     *
     * @param views the views
     * @return the views and those they extend
     * @throws IllegalArgumentException if a view is unknown
     */
    public static Set<String> withExtended(Iterable<String> views) {
        Set<String> all = new LinkedHashSet<>();
        all.add(BASIC);
        for (String view : views) {
            add(view, all);
        }
        return Set.copyOf(all);
    }

    private static void add(String view, Set<String> all) {
        List<String> extended = EXTENDED.get(view);
        if (extended == null) {
            throw new IllegalArgumentException("Unknown attribute view: " + view);
        }
        if (all.add(view)) {
            extended.forEach(parent -> add(parent, all));
        }
    }
}
