package be.imgn.gaia.core.platform;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The directories where one application stores its files: its own subdirectories of the platform's {@link Directories},
 * one for each kind of file, never shared with another kind.
 *
 * <p>Get them from {@link Platform#applicationDirectories(String)} and its overloads. The directories are not created:
 * create each one before writing in it.
 *
 * <p>Before 0.1.2, it was {@code be.imgn.gaia.platform.ApplicationDirectories}, in its own module,
 * {@code gaia-platform}.
 *
 * @since 0.1.2
 */
public final class ApplicationDirectories {

    private static final String FORBIDDEN_CHARACTERS = "/\\:<>\"|?*";
    // Windows opens the device rather than a file for these names, whatever their case, and even with an extension.
    private static final Pattern WINDOWS_DEVICE =
            Pattern.compile("(?:CON|PRN|AUX|NUL|COM[0-9¹²³]|LPT[0-9¹²³]) *(?:\\..*)?", Pattern.CASE_INSENSITIVE);

    private final Path config;
    private final Path data;
    private final Path state;
    private final Path cache;
    private final @Nullable Path runtime;

    private ApplicationDirectories(Path config, Path data, Path state, Path cache, @Nullable Path runtime) {
        this.config = config;
        this.data = data;
        this.state = state;
        this.cache = cache;
        this.runtime = runtime;
    }

    static ApplicationDirectories of(
            Platform platform, @Nullable String qualifier, @Nullable String vendor, String application) {
        requireNonNull(platform, "platform");
        if (qualifier != null) {
            checkName(qualifier, "qualifier");
        }
        if (vendor != null) {
            checkName(vendor, "vendor");
        }
        checkName(application, "application");
        Directories directories = platform.directories();
        if (platform.isWindows()) {
            return windows(directories, vendor, application);
        }
        if (platform.isMacOS()) {
            return macOS(directories, qualifier, vendor, application);
        }
        return xdg(directories, application);
    }

    /**
     * Checks a name as the overloads of {@link Platform#applicationDirectories(String)} do: the name of an application,
     * of its vendor, or of its vendor's qualifier. A name must be valid on every platform, not only on the one the JVM
     * runs on, so that code can check it before it runs, such as an annotation processor checking the names an
     * annotation gives.
     *
     * <pre>{@code
     * ApplicationDirectories.checkName("Quuz", "application");
     * }</pre>
     *
     * @param name the name to check
     * @param role what the name is, such as {@code vendor}, which the message of the exception starts with: {@code The
     *     vendor must not start or end with whitespace: " bar"}; a control character of the name is written in the
     *     message as a Unicode escape, as <code>&#92;u0009</code> for a tab
     * @throws IllegalArgumentException if the name is blank, {@code .} or {@code ..}, starts or ends with whitespace,
     *     contains a control character or any of {@code / \ : < > " | ? *}, which some platform forbids in a file name,
     *     ends with a dot, or is the name of a Windows device, such as {@code CON}, {@code NUL}, {@code COM1} or
     *     {@code LPT9}, with or without an extension
     */
    public static void checkName(String name, String role) {
        requireNonNull(role, "role");
        requireNonNull(name, role);
        // Windows is the strictest: it forbids control characters, a trailing space and FORBIDDEN_CHARACTERS in a file
        // name.
        checkArgument(
                !name.isBlank() && !".".equals(name) && !"..".equals(name),
                "The %s must be a name, not %s",
                role,
                quoted(name));
        checkArgument(
                name.strip().equals(name), "The %s must not start or end with whitespace: %s", role, quoted(name));
        checkArgument(
                name.chars().noneMatch(c -> Character.isISOControl(c) || FORBIDDEN_CHARACTERS.indexOf(c) >= 0),
                "The %s must not contain a control character or any of %s: %s",
                role,
                FORBIDDEN_CHARACTERS,
                quoted(name));
        // Windows strips a trailing dot, so that "Quuz." and "Quuz" are the same directory.
        checkArgument(!name.endsWith("."), "The %s must not end with a dot: %s", role, quoted(name));
        checkArgument(
                !WINDOWS_DEVICE.matcher(name).matches(),
                "The %s must not be the name of a Windows device: %s",
                role,
                quoted(name));
    }

    // A control character would end up raw in the message, and in whatever shows it, such as a compiler diagnostic.
    private static String quoted(String name) {
        StringBuilder quoted = new StringBuilder("\"");
        name.chars()
                .forEach(c -> quoted.append(
                        Character.isISOControl(c)
                                ? String.format(Locale.ROOT, "\\u%04x", c)
                                : String.valueOf((char) c)));
        return quoted.append('"').toString();
    }

    // Roaming data follows the user across machines; state and cache stay on this one.
    private static ApplicationDirectories windows(
            Directories directories, @Nullable String vendor, String application) {
        Path roaming = under(directories.configHome(), vendor, application);
        Path local = under(directories.cacheHome(), vendor, application);
        return new ApplicationDirectories(
                roaming.resolve("config"),
                roaming.resolve("data"),
                local.resolve("state"),
                local.resolve("cache"),
                null);
    }

    private static Path under(Path base, @Nullable String vendor, String application) {
        return vendor == null ? base.resolve(application) : base.resolve(vendor).resolve(application);
    }

    // Application Support holds configuration, data and state alike, hence one subdirectory for each.
    private static ApplicationDirectories macOS(
            Directories directories, @Nullable String qualifier, @Nullable String vendor, String application) {
        String name = qualifier == null || vendor == null
                ? application
                : String.join(".", qualifier, vendor, application).replace(' ', '-');
        Path applicationSupport = directories.dataHome().resolve(name);
        return new ApplicationDirectories(
                applicationSupport.resolve("config"),
                applicationSupport.resolve("data"),
                applicationSupport.resolve("state"),
                directories.cacheHome().resolve(name),
                null);
    }

    private static ApplicationDirectories xdg(Directories directories, String application) {
        String name = application.toLowerCase(Locale.ROOT).replace(' ', '-');
        return new ApplicationDirectories(
                directories.configHome().resolve(name),
                directories.dataHome().resolve(name),
                directories.stateHome().resolve(name),
                directories.cacheHome().resolve(name),
                directories
                        .runtimeDir()
                        .map(runtimeDir -> runtimeDir.resolve(name))
                        .orElse(null));
    }

    /**
     * Returns the directory for the application's configuration files.
     *
     * @return the configuration directory
     */
    public Path config() {
        return config;
    }

    /**
     * Returns the directory for the application's data files.
     *
     * @return the data directory
     */
    public Path data() {
        return data;
    }

    /**
     * Returns the directory for the application's state: data that should persist between restarts but is not important
     * or portable enough for {@link #data()}, such as history, logs or recently used files.
     *
     * @return the state directory
     */
    public Path state() {
        return state;
    }

    /**
     * Returns the directory for the application's non-essential, cached data.
     *
     * @return the cache directory
     */
    public Path cache() {
        return cache;
    }

    /**
     * Returns the directory for the application's runtime files, such as sockets and named pipes, where the platform
     * has a runtime directory.
     *
     * @return the runtime directory, or empty if the platform has none
     */
    public Optional<Path> runtime() {
        return Optional.ofNullable(runtime);
    }
}
