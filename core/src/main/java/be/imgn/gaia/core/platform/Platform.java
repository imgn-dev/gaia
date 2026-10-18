package be.imgn.gaia.core.platform;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.platform.internal.Platforms;
import be.imgn.gaia.core.system.Environment;
import be.imgn.gaia.core.system.SystemProperties;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

/**
 * The platform the JVM runs on: its operating system and the directories applications use on it.
 *
 * <p>Use {@link #current()} to get the platform of the running JVM. Code that receives a {@code Platform} instead of
 * calling {@code current()} itself can be tested against any platform.
 *
 * <p>At most one of {@link #isLinux()}, {@link #isMacOS()} and {@link #isWindows()} is {@code true}. On other Unix-like
 * systems, such as FreeBSD, all three are {@code false}, and the directories follow the XDG Base Directory
 * Specification, as on Linux.
 *
 * <p>Before 0.1.2, it was {@code be.imgn.gaia.platform.Platform}, in its own module, {@code gaia-platform}.
 *
 * @since 0.1.2
 */
public interface Platform {

    /**
     * Returns the platform of the running JVM: detected from its system properties and environment variables, with the
     * {@linkplain FileSystems#getDefault() default file system}. It is detected once.
     *
     * @return the current platform
     */
    static Platform current() {
        return Platforms.current();
    }

    /**
     * Returns a builder that detects a platform from the given sources, or from the running JVM's for any source not
     * given.
     *
     * @return a new builder
     */
    static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Returns whether this platform runs Linux.
     *
     * @return {@code true} on Linux; {@code false} by default
     */
    default boolean isLinux() {
        return false;
    }

    /**
     * Returns whether this platform runs macOS.
     *
     * @return {@code true} on macOS; {@code false} by default
     */
    default boolean isMacOS() {
        return false;
    }

    /**
     * Returns whether this platform runs Windows.
     *
     * @return {@code true} on Windows; {@code false} by default
     */
    default boolean isWindows() {
        return false;
    }

    /**
     * Returns the file system of this platform, which all its paths belong to.
     *
     * @return the file system
     */
    FileSystem fileSystem();

    /**
     * Returns the directories where applications store their files on this platform.
     *
     * @return the directories of this platform
     */
    Directories directories();

    /**
     * Returns the directories of an application, named by its name alone.
     *
     * <ul>
     *   <li>Linux, Unix: {@code ~/.config/app}, {@code ~/.local/share/app}, {@code ~/.local/state/app},
     *       {@code ~/.cache/app} and {@code $XDG_RUNTIME_DIR/app}, under the {@linkplain #directories() base
     *       directories}, where {@code app} is the name in lower case with spaces replaced by {@code -}
     *   <li>macOS: {@code config}, {@code data} and {@code state} in {@code ~/Library/Application Support/App}, and
     *       {@code ~/Library/Caches/App}
     *   <li>Windows: {@code config} and {@code data} in {@code %APPDATA%\App}, {@code state} and {@code cache} in
     *       {@code %LOCALAPPDATA%\App}
     * </ul>
     *
     * @param application the name of the application, such as {@code Quuz}
     * @return the directories of the application
     * @throws IllegalArgumentException if the name is blank, {@code .} or {@code ..}, starts or ends with whitespace,
     *     or contains a control character or any of {@code / \ : < > " | ? *}, which some platform forbids in a file
     *     name
     */
    default ApplicationDirectories applicationDirectories(String application) {
        return ApplicationDirectories.of(this, null, null, application);
    }

    /**
     * Returns the directories of an application, named by its vendor and its name. Only Windows uses the vendor, as a
     * directory above the application's: {@code %APPDATA%\Vendor\App} and {@code %LOCALAPPDATA%\Vendor\App}. Otherwise,
     * the directories are those of {@link #applicationDirectories(String)}.
     *
     * @param vendor the name of the organization or person that makes the application, such as {@code bar}
     * @param application the name of the application, such as {@code Quuz}
     * @return the directories of the application
     * @throws IllegalArgumentException if a name is blank, {@code .} or {@code ..}, starts or ends with whitespace, or
     *     contains a control character or any of {@code / \ : < > " | ? *}, which some platform forbids in a file name
     */
    default ApplicationDirectories applicationDirectories(String vendor, String application) {
        requireNonNull(vendor, "vendor");
        return ApplicationDirectories.of(this, null, vendor, application);
    }

    /**
     * Returns the directories of an application, named by its vendor's qualifier, its vendor and its name. Only macOS
     * uses the qualifier: its directories are named after the bundle-style identifier {@code qualifier.vendor.App},
     * such as {@code ~/Library/Application Support/foo.bar.Quuz}, with spaces replaced by {@code -}. Otherwise, the
     * directories are those of {@link #applicationDirectories(String, String)}.
     *
     * @param qualifier the top-level domain of the vendor, such as {@code foo}
     * @param vendor the name of the organization or person that makes the application, such as {@code bar}
     * @param application the name of the application, such as {@code Quuz}
     * @return the directories of the application
     * @throws IllegalArgumentException if a name is blank, {@code .} or {@code ..}, starts or ends with whitespace, or
     *     contains a control character or any of {@code / \ : < > " | ? *}, which some platform forbids in a file name
     */
    default ApplicationDirectories applicationDirectories(String qualifier, String vendor, String application) {
        requireNonNull(qualifier, "qualifier");
        requireNonNull(vendor, "vendor");
        return ApplicationDirectories.of(this, qualifier, vendor, application);
    }

    /**
     * Detects a {@link Platform} from its sources. Each source defaults to the running JVM's:
     * {@link SystemProperties#system()}, {@link Environment#system()} and {@link FileSystems#getDefault()}.
     *
     * <p>The operating system comes from the {@code os.name} property; any value not recognized as Linux, macOS or
     * Windows is taken for another Unix-like system. Every path of the platform belongs to the file system.
     */
    final class Builder {

        private SystemProperties systemProperties = SystemProperties.system();
        private Environment environment = Environment.system();
        private FileSystem fileSystem = FileSystems.getDefault();

        private Builder() {}

        /**
         * Sets the system properties to read; {@code os.name} and {@code user.home} are required.
         *
         * @param systemProperties the system properties
         * @return this builder
         */
        public Builder systemProperties(SystemProperties systemProperties) {
            this.systemProperties = requireNonNull(systemProperties, "systemProperties");
            return this;
        }

        /**
         * Sets the environment variables to read.
         *
         * @param environment the environment variables
         * @return this builder
         */
        public Builder environment(Environment environment) {
            this.environment = requireNonNull(environment, "environment");
            return this;
        }

        /**
         * Sets the file system the platform's paths belong to.
         *
         * @param fileSystem the file system
         * @return this builder
         */
        public Builder fileSystem(FileSystem fileSystem) {
            this.fileSystem = requireNonNull(fileSystem, "fileSystem");
            return this;
        }

        /**
         * Detects the platform from this builder's sources. The sources are read now; later changes to them do not
         * affect the platform.
         *
         * @return the platform
         * @throws java.util.NoSuchElementException if {@code os.name} or {@code user.home} is not set
         * @throws java.nio.file.InvalidPathException if {@code user.home}, or an environment variable the platform
         *     reads a directory from, is not a valid path on the file system
         */
        public Platform detect() {
            return Platforms.detect(systemProperties, environment, fileSystem);
        }
    }
}
