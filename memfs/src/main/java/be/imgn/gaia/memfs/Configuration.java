package be.imgn.gaia.memfs;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import be.imgn.gaia.memfs.internal.Syntax;
import be.imgn.gaia.memfs.internal.Views;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.Normalizer;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * How a memory file system behaves: its path style, roots, working directory, capacity, name matching, attribute views,
 * default attributes, clock and features. Configurations are immutable; {@link #toBuilder()} starts a changed copy.
 *
 * <pre>{@code
 * Configuration small = Configuration.windows().toBuilder()
 *         .roots("C:\\", "D:\\")
 *         .maxSize(16 * 1024 * 1024)
 *         .currentUser("alice", "staff")
 *         .build();
 * }</pre>
 *
 * <p>Permissions are always stored. They are enforced only when a {@linkplain Builder#currentUser(String, String...)
 * current user} is configured: then the POSIX permissions of the {@code posix} view, the read-only flag of the
 * {@code dos} view and the entries of a non-empty access control list of the {@code acl} view are checked against that
 * user and their groups, and a denied operation throws {@link java.nio.file.AccessDeniedException}. The user
 * {@code root} is never denied.
 *
 * @since 0.1.1
 */
public final class Configuration {

    private static final int DEFAULT_BLOCK_SIZE = 8192;
    private static final long DEFAULT_MAX_SIZE = 4L * 1024 * 1024 * 1024;
    private static final int DEFAULT_MAX_NAME_LENGTH = 255;
    private static final Duration DEFAULT_POLLING_INTERVAL = Duration.ofSeconds(1);

    private final PathStyle style;
    private final List<String> roots;
    private final String workingDirectory;
    private final int blockSize;
    private final long maxSize;
    private final NameMatching nameMatching;
    private final Normalizer.@Nullable Form unicodeNormalization;
    private final Set<String> attributeViews;
    private final Map<String, Object> defaultAttributes;
    private final InstantSource clock;
    private final Duration watchServicePollingInterval;
    private final Set<Feature> features;
    private final int maxNameLength;
    private final @Nullable String currentUser;
    private final List<String> currentGroups;

    private Configuration(Builder builder) {
        this.style = builder.style;
        this.roots = List.copyOf(builder.roots);
        this.workingDirectory = builder.workingDirectory;
        this.blockSize = builder.blockSize;
        this.maxSize = builder.maxSize;
        this.nameMatching = builder.nameMatching;
        this.unicodeNormalization = builder.unicodeNormalization;
        this.attributeViews = Views.withExtended(builder.attributeViews);
        this.defaultAttributes = Map.copyOf(builder.defaultAttributes);
        this.clock = builder.clock;
        this.watchServicePollingInterval = builder.watchServicePollingInterval;
        this.features = Set.copyOf(builder.features);
        this.maxNameLength = builder.maxNameLength;
        this.currentUser = builder.currentUser;
        this.currentGroups = List.copyOf(builder.currentGroups);
    }

    /**
     * Returns the configuration of a Linux-like file system: the root {@code /}, the working directory {@code /work},
     * names matched exactly, and the {@code basic}, {@code owner}, {@code posix} and {@code unix} attribute views.
     *
     * @return the configuration
     */
    public static Configuration unix() {
        return newBuilder(PathStyle.UNIX).build();
    }

    /**
     * Returns the configuration of a macOS-like file system: as {@link #unix()}, but names match ignoring case and
     * Unicode normalization, so that {@code Café} written with a composed or a decomposed {@code é} and {@code CAFÉ}
     * name the same file.
     *
     * @return the configuration
     */
    public static Configuration macOS() {
        return newBuilder(PathStyle.UNIX)
                .nameMatching(NameMatching.IGNORE_CASE_UNICODE)
                .unicodeNormalization(Normalizer.Form.NFD)
                .build();
    }

    /**
     * Returns the configuration of a Windows-like file system: the root {@code C:\}, the working directory
     * {@code C:\work}, names matched ignoring case, and the {@code basic}, {@code owner}, {@code dos}, {@code acl} and
     * {@code user} attribute views.
     *
     * @return the configuration
     */
    public static Configuration windows() {
        return newBuilder(PathStyle.WINDOWS).build();
    }

    /**
     * Returns the configuration that looks like the file system of the operating system the JVM runs on:
     * {@link #windows()}, {@link #macOS()} or {@link #unix()}.
     *
     * @return the configuration
     */
    public static Configuration forCurrentPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return windows();
        }
        return os.startsWith("mac") ? macOS() : unix();
    }

    /**
     * Returns a builder that starts with the defaults of a path style: those of {@link #unix()} or {@link #windows()}.
     * Every file system also has a block size of 8 KiB, a capacity of 4 GiB, names of 255 characters at most, the
     * system clock, a watch service that polls every second, and every {@link Feature}.
     *
     * @param style the path style
     * @return a new builder
     */
    public static Builder newBuilder(PathStyle style) {
        requireNonNull(style, "style");
        return new Builder(style);
    }

    /**
     * Returns a builder that starts with this configuration.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Returns the path style.
     *
     * @return the path style
     */
    public PathStyle style() {
        return style;
    }

    /**
     * Returns the root directories, as paths write them.
     *
     * @return the roots, in order
     */
    public List<String> roots() {
        return roots;
    }

    /**
     * Returns the working directory, which relative paths are resolved against. It is created with the file system.
     *
     * @return the absolute path of the working directory
     */
    public String workingDirectory() {
        return workingDirectory;
    }

    /**
     * Returns the size of the blocks the content of files is stored in. A file takes up whole blocks.
     *
     * @return the block size, in bytes
     */
    public int blockSize() {
        return blockSize;
    }

    /**
     * Returns the capacity of the file system: writing past it throws a {@link java.nio.file.FileSystemException}.
     *
     * @return the capacity, in bytes
     */
    public long maxSize() {
        return maxSize;
    }

    /**
     * Returns how names are matched when files are looked up.
     *
     * @return the name matching
     */
    public NameMatching nameMatching() {
        return nameMatching;
    }

    /**
     * Returns the Unicode normalization names are matched under, if any.
     *
     * @return the normalization form, or empty if names are matched as they are
     */
    public Optional<Normalizer.Form> unicodeNormalization() {
        return Optional.ofNullable(unicodeNormalization);
    }

    /**
     * Returns the supported attribute views, with {@code basic} and those the configured views extend.
     *
     * @return the names of the views
     */
    public Set<String> attributeViews() {
        return attributeViews;
    }

    /**
     * Returns the attributes new files get unless they are given others.
     *
     * @return the attributes, by {@code view:name}
     */
    public Map<String, Object> defaultAttributes() {
        return defaultAttributes;
    }

    /**
     * Returns the clock that dates the creation, modification and access of files.
     *
     * @return the clock
     */
    public InstantSource clock() {
        return clock;
    }

    /**
     * Returns how often a watch service looks for changes in the directories it watches.
     *
     * @return the interval
     */
    public Duration watchServicePollingInterval() {
        return watchServicePollingInterval;
    }

    /**
     * Returns the optional features the file system has.
     *
     * @return the features
     */
    public Set<Feature> features() {
        return features;
    }

    /**
     * Returns how long a name may be.
     *
     * @return the maximum length of a name, in characters
     */
    public int maxNameLength() {
        return maxNameLength;
    }

    /**
     * Returns the user permissions are checked against, if they are enforced.
     *
     * @return the user, or empty if permissions are not enforced
     */
    public Optional<String> currentUser() {
        return Optional.ofNullable(currentUser);
    }

    /**
     * Returns the groups of the {@linkplain #currentUser() current user}.
     *
     * @return the groups, the first being the group of the files the user creates
     */
    public List<String> currentGroups() {
        return currentGroups;
    }

    @Override
    public String toString() {
        return "Configuration[" + style + ", roots=" + roots + ", workingDirectory=" + workingDirectory + "]";
    }

    /** A builder of {@link Configuration}s. */
    public static final class Builder {

        private final PathStyle style;
        private List<String> roots;
        private String workingDirectory;
        private int blockSize = DEFAULT_BLOCK_SIZE;
        private long maxSize = DEFAULT_MAX_SIZE;
        private NameMatching nameMatching;
        private Normalizer.@Nullable Form unicodeNormalization;
        private List<String> attributeViews;
        private final Map<String, Object> defaultAttributes = new LinkedHashMap<>();
        private InstantSource clock = InstantSource.system();
        private Duration watchServicePollingInterval = DEFAULT_POLLING_INTERVAL;
        private Set<Feature> features = EnumSet.allOf(Feature.class);
        private int maxNameLength = DEFAULT_MAX_NAME_LENGTH;
        private @Nullable String currentUser;
        private List<String> currentGroups = List.of();

        private Builder(PathStyle style) {
            this.style = style;
            if (style == PathStyle.UNIX) {
                roots = List.of("/");
                workingDirectory = "/work";
                nameMatching = NameMatching.EXACT;
                attributeViews = List.of(Views.BASIC, Views.OWNER, Views.POSIX, Views.UNIX);
            } else {
                roots = List.of("C:\\");
                workingDirectory = "C:\\work";
                nameMatching = NameMatching.IGNORE_CASE_UNICODE;
                attributeViews = List.of(Views.BASIC, Views.OWNER, Views.DOS, Views.ACL, Views.USER);
            }
        }

        private Builder(Configuration configuration) {
            this.style = configuration.style;
            this.roots = configuration.roots;
            this.workingDirectory = configuration.workingDirectory;
            this.blockSize = configuration.blockSize;
            this.maxSize = configuration.maxSize;
            this.nameMatching = configuration.nameMatching;
            this.unicodeNormalization = configuration.unicodeNormalization;
            this.attributeViews = List.copyOf(configuration.attributeViews);
            this.defaultAttributes.putAll(configuration.defaultAttributes);
            this.clock = configuration.clock;
            this.watchServicePollingInterval = configuration.watchServicePollingInterval;
            this.features = EnumSet.noneOf(Feature.class);
            this.features.addAll(configuration.features);
            this.maxNameLength = configuration.maxNameLength;
            this.currentUser = configuration.currentUser;
            this.currentGroups = configuration.currentGroups;
        }

        /**
         * Sets the root directories. A Unix-style file system has the one root {@code /}; a Windows-style one has
         * drives, such as {@code C:\}, and UNC shares, such as {@code \\server\share\}.
         *
         * @param first the first root
         * @param more the other roots
         * @return this builder
         * @throws IllegalArgumentException if a root is not the root of an absolute path of the style, is given twice,
         *     or a Unix-style file system is given another root than {@code /}
         */
        public Builder roots(String first, String... more) {
            requireNonNull(first, "first");
            requireNonNull(more, "more");
            Syntax syntax = Syntax.of(style);
            List<String> checked = new ArrayList<>();
            checked.add(syntax.checkRoot(first));
            for (String root : more) {
                checked.add(syntax.checkRoot(requireNonNull(root, "more")));
            }
            checkArgument(
                    checked.stream().map(syntax::rootKey).distinct().count() == checked.size(),
                    "A root is given twice: %s",
                    checked);
            checkArgument(
                    style == PathStyle.WINDOWS || checked.size() == 1,
                    "A Unix-style file system has one root: %s",
                    checked);
            roots = List.copyOf(checked);
            return this;
        }

        /**
         * Sets the working directory, which relative paths are resolved against. It is created with the file system,
         * with the directories it is in.
         *
         * @param path the absolute path of the directory, on one of the roots
         * @return this builder
         */
        public Builder workingDirectory(String path) {
            workingDirectory = requireNonNull(path, "path");
            return this;
        }

        /**
         * Sets the size of the blocks the content of files is stored in.
         *
         * @param bytes the block size, in bytes
         * @return this builder
         * @throws IllegalArgumentException if the size is not positive
         */
        public Builder blockSize(int bytes) {
            checkArgument(bytes > 0, "The block size must be positive: %s", bytes);
            blockSize = bytes;
            return this;
        }

        /**
         * Sets the capacity of the file system. Writing past it throws a {@link java.nio.file.FileSystemException}.
         *
         * @param bytes the capacity, in bytes
         * @return this builder
         * @throws IllegalArgumentException if the capacity is not positive
         */
        public Builder maxSize(long bytes) {
            checkArgument(bytes > 0, "The capacity must be positive: %s", bytes);
            maxSize = bytes;
            return this;
        }

        /**
         * Sets how names are matched when files are looked up.
         *
         * @param matching the name matching
         * @return this builder
         */
        public Builder nameMatching(NameMatching matching) {
            nameMatching = requireNonNull(matching, "matching");
            return this;
        }

        /**
         * Sets the Unicode normalization names are matched under: with any form, a name written with composed or
         * decomposed characters names the same file. Names are still shown as they were created.
         *
         * @param form the normalization form, or {@code null} to match names as they are
         * @return this builder
         */
        public Builder unicodeNormalization(Normalizer.@Nullable Form form) {
            unicodeNormalization = form;
            return this;
        }

        /**
         * Sets the supported attribute views: any of {@code basic}, {@code owner}, {@code posix}, {@code unix},
         * {@code dos}, {@code acl} and {@code user}. The file system also supports {@code basic} and the views these
         * extend, as {@code posix} extends {@code owner}.
         *
         * @param views the names of the views
         * @return this builder
         * @throws IllegalArgumentException if a view is unknown
         */
        public Builder attributeViews(String... views) {
            List<String> checked = List.of(requireNonNull(views, "views"));
            Views.withExtended(checked);
            attributeViews = checked;
            return this;
        }

        /**
         * Sets an attribute new files get unless they are given it: {@code owner:owner} (a user name),
         * {@code posix:group} (a group name), {@code posix:permissions} (a {@code Set<PosixFilePermission>} or a string
         * such as {@code rw-r--r--}), {@code dos:readonly}, {@code dos:hidden}, {@code dos:system} or
         * {@code dos:archive} (a {@code Boolean}), or {@code acl:acl} (a {@code List<AclEntry>}). Its view must be
         * supported when the configuration is built.
         *
         * @param attribute the attribute, as {@code view:name}
         * @param value the value
         * @return this builder
         * @throws IllegalArgumentException if the attribute is not one of these, or the value has the wrong type
         */
        public Builder defaultAttribute(String attribute, Object value) {
            requireNonNull(attribute, "attribute");
            requireNonNull(value, "value");
            defaultAttributes.put(attribute, checkDefault(attribute, value));
            return this;
        }

        private static Object checkDefault(String attribute, Object value) {
            return switch (attribute) {
                case "owner:owner", "posix:group" -> checkType(attribute, value, String.class);
                case "posix:permissions" -> permissions(value);
                case "dos:readonly", "dos:hidden", "dos:system", "dos:archive" ->
                    checkType(attribute, value, Boolean.class);
                case "acl:acl" -> acl(value);
                default -> throw new IllegalArgumentException("Not an attribute with a default: " + attribute);
            };
        }

        private static <T> T checkType(String attribute, Object value, Class<T> type) {
            checkArgument(type.isInstance(value), "%s must be a %s: %s", attribute, type.getSimpleName(), value);
            return type.cast(value);
        }

        private static Set<PosixFilePermission> permissions(Object value) {
            if (value instanceof String text) {
                return PosixFilePermissions.fromString(text);
            }
            Collection<?> collection = checkType("posix:permissions", value, Collection.class);
            Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
            for (Object element : collection) {
                permissions.add(checkType("posix:permissions", element, PosixFilePermission.class));
            }
            return Set.copyOf(permissions);
        }

        private static List<AclEntry> acl(Object value) {
            List<?> list = checkType("acl:acl", value, List.class);
            return list.stream()
                    .map(entry -> checkType("acl:acl", entry, AclEntry.class))
                    .toList();
        }

        /**
         * Sets the clock that dates the creation, modification and access of files.
         *
         * @param source the clock
         * @return this builder
         */
        public Builder clock(InstantSource source) {
            clock = requireNonNull(source, "source");
            return this;
        }

        /**
         * Sets how often a watch service looks for changes in the directories it watches.
         *
         * @param interval the interval
         * @return this builder
         * @throws IllegalArgumentException if the interval is not positive
         */
        public Builder watchServicePollingInterval(Duration interval) {
            requireNonNull(interval, "interval");
            checkArgument(interval.isPositive(), "The polling interval must be positive: %s", interval);
            watchServicePollingInterval = interval;
            return this;
        }

        /**
         * Sets the optional features the file system has; it lacks the others.
         *
         * @param supported the features
         * @return this builder
         */
        public Builder features(Feature... supported) {
            requireNonNull(supported, "supported");
            Set<Feature> set = EnumSet.noneOf(Feature.class);
            for (Feature feature : supported) {
                set.add(requireNonNull(feature, "supported"));
            }
            features = set;
            return this;
        }

        /**
         * Sets how long a name may be. Creating a file with a longer name throws a
         * {@link java.nio.file.FileSystemException}.
         *
         * @param characters the maximum length, in characters
         * @return this builder
         * @throws IllegalArgumentException if the length is not positive
         */
        public Builder maxNameLength(int characters) {
            checkArgument(characters > 0, "The maximum name length must be positive: %s", characters);
            maxNameLength = characters;
            return this;
        }

        /**
         * Enforces permissions, checking them against a user and their groups. The files the user creates belong to
         * them and to their first group, unless {@code owner:owner} and {@code posix:group} have defaults.
         *
         * @param user the user name
         * @param groups the groups of the user
         * @return this builder
         */
        public Builder currentUser(String user, String... groups) {
            requireNonNull(user, "user");
            requireNonNull(groups, "groups");
            currentUser = user;
            currentGroups = List.of(groups);
            return this;
        }

        /**
         * Stops enforcing permissions. They are still stored.
         *
         * @return this builder
         */
        public Builder noCurrentUser() {
            currentUser = null;
            currentGroups = List.of();
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return the configuration
         * @throws IllegalArgumentException if the working directory is not an absolute path on one of the roots, the
         *     block size is larger than the capacity, or a default attribute is of a view that is not supported
         */
        public Configuration build() {
            checkWorkingDirectory();
            checkArgument(blockSize <= maxSize, "The block size %s is larger than the capacity %s", blockSize, maxSize);
            Set<String> views = Views.withExtended(attributeViews);
            for (String attribute : defaultAttributes.keySet()) {
                String view = attribute.substring(0, attribute.indexOf(':'));
                checkArgument(views.contains(view), "The default attribute %s is of an unsupported view", attribute);
            }
            return new Configuration(this);
        }

        private void checkWorkingDirectory() {
            Syntax syntax = Syntax.of(style);
            Syntax.Parsed parsed = syntax.parse(workingDirectory);
            String root = parsed.root();
            if (root == null || !syntax.isAbsolute(root)) {
                throw new IllegalArgumentException("Not an absolute path: " + workingDirectory);
            }
            String key = syntax.rootKey(root);
            checkArgument(
                    roots.stream().map(syntax::rootKey).anyMatch(key::equals),
                    "The working directory %s is not on a root: %s",
                    workingDirectory,
                    roots);
        }
    }
}
