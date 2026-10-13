package be.imgn.gaia.platform;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The directories where one application stores its files: its own subdirectories of the platform's {@link Directories},
 * one for each kind of file, never shared with another kind.
 *
 * <p>Get them from {@link Platform#applicationDirectories(String)} and its overloads. The directories are not created:
 * create each one before writing in it.
 *
 * @since 0.1.1
 */
public final class ApplicationDirectories {

    private static final String FORBIDDEN_CHARACTERS = "/\\:<>\"|?*";

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
        Objects.requireNonNull(platform, "platform");
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

    // A name must be valid on every platform, not only on the one the JVM runs on. Windows is the strictest: it forbids
    // control characters, a trailing space and FORBIDDEN_CHARACTERS in a file name.
    private static void checkName(String name, String role) {
        Objects.requireNonNull(name, role);
        checkArgument(
                !name.isBlank() && !".".equals(name) && !"..".equals(name),
                "The %s must be a name, not '%s'",
                role,
                name);
        checkArgument(name.strip().equals(name), "The %s must not start or end with whitespace: '%s'", role, name);
        checkArgument(
                name.chars().noneMatch(c -> Character.isISOControl(c) || FORBIDDEN_CHARACTERS.indexOf(c) >= 0),
                "The %s must not contain a control character or any of %s: %s",
                role,
                FORBIDDEN_CHARACTERS,
                name);
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
