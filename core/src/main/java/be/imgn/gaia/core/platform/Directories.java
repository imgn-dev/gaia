package be.imgn.gaia.core.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The base directories where applications store their files, as defined by the <a
 * href="https://specifications.freedesktop.org/basedir-spec/latest/">XDG Base Directory Specification</a>.
 *
 * <p>On Linux and other Unix-like systems, each directory comes from its XDG environment variable, or the
 * specification's default when the variable is unset, empty or not an absolute path. Windows has no such variables: its
 * directories are computed from the environment variables Windows sets ({@code APPDATA}, {@code LOCALAPPDATA},
 * {@code ProgramData}) and fall back to their default locations. macOS uses its standard locations under
 * {@code ~/Library} and {@code /Library}.
 *
 * <p>These are base directories, shared by all applications: put files in a subdirectory named after the application.
 * On Windows and macOS several of them are the same directory, so an application that keeps, for example, its cache
 * apart from its state needs distinct subdirectories for them.
 *
 * <p>Before 0.1.2, it was {@code be.imgn.gaia.platform.Directories}, in its own module, {@code gaia-platform}.
 *
 * @since 0.1.2
 */
public interface Directories {

    /**
     * Returns the user's home directory.
     *
     * <ul>
     *   <li>Linux, Unix: {@code $HOME}, or the {@code user.home} system property if it is unset or not absolute
     *   <li>macOS, Windows: the {@code user.home} system property
     * </ul>
     *
     * @return the home directory
     */
    Path home();

    /**
     * Returns the directory for user-specific data files.
     *
     * <ul>
     *   <li>Linux, Unix: {@code $XDG_DATA_HOME}, default {@code ~/.local/share}
     *   <li>macOS: {@code ~/Library/Application Support}
     *   <li>Windows: {@code %APPDATA%}, default {@code ~\AppData\Roaming}
     * </ul>
     *
     * @return the user data directory
     */
    Path dataHome();

    /**
     * Returns the directory for user-specific configuration files.
     *
     * <ul>
     *   <li>Linux, Unix: {@code $XDG_CONFIG_HOME}, default {@code ~/.config}
     *   <li>macOS: {@code ~/Library/Application Support}
     *   <li>Windows: {@code %APPDATA%}, default {@code ~\AppData\Roaming}
     * </ul>
     *
     * @return the user configuration directory
     */
    Path configHome();

    /**
     * Returns the directory for user-specific state: data that should persist between restarts but is not important or
     * portable enough for {@link #dataHome()}, such as history, logs or recently used files.
     *
     * <ul>
     *   <li>Linux, Unix: {@code $XDG_STATE_HOME}, default {@code ~/.local/state}
     *   <li>macOS: {@code ~/Library/Application Support}
     *   <li>Windows: {@code %LOCALAPPDATA%}, default {@code ~\AppData\Local}
     * </ul>
     *
     * @return the user state directory
     */
    Path stateHome();

    /**
     * Returns the directory for user-specific non-essential, cached data.
     *
     * <ul>
     *   <li>Linux, Unix: {@code $XDG_CACHE_HOME}, default {@code ~/.cache}
     *   <li>macOS: {@code ~/Library/Caches}
     *   <li>Windows: {@code %LOCALAPPDATA%}, default {@code ~\AppData\Local}
     * </ul>
     *
     * @return the user cache directory
     */
    Path cacheHome();

    /**
     * Returns the directory for user-specific runtime files, such as sockets and named pipes.
     *
     * <ul>
     *   <li>Linux, Unix: {@code $XDG_RUNTIME_DIR}, empty if it is unset; the specification has no default
     *   <li>macOS, Windows: always empty, as there is no equivalent
     * </ul>
     *
     * @return the user runtime directory, or empty if there is none
     */
    Optional<Path> runtimeDir();

    /**
     * Returns the system-wide directories to search for data files, most important first, after {@link #dataHome()}.
     *
     * <ul>
     *   <li>Linux, Unix: {@code $XDG_DATA_DIRS}, default {@code /usr/local/share:/usr/share}
     *   <li>macOS: {@code /Library/Application Support}
     *   <li>Windows: {@code %ProgramData%}, default {@code %SystemDrive%\ProgramData}
     * </ul>
     *
     * @return the system data directories, in order of importance
     */
    List<Path> dataDirs();

    /**
     * Returns the system-wide directories to search for configuration files, most important first, after
     * {@link #configHome()}.
     *
     * <ul>
     *   <li>Linux, Unix: {@code $XDG_CONFIG_DIRS}, default {@code /etc/xdg}
     *   <li>macOS: {@code /Library/Application Support}
     *   <li>Windows: {@code %ProgramData%}, default {@code %SystemDrive%\ProgramData}
     * </ul>
     *
     * @return the system configuration directories, in order of importance
     */
    List<Path> configDirs();
}
