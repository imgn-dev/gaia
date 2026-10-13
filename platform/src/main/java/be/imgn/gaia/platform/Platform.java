package be.imgn.gaia.platform;

import be.imgn.gaia.core.system.Environment;
import be.imgn.gaia.core.system.SystemProperties;
import be.imgn.gaia.platform.internal.Platforms;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.Objects;

/**
 * The platform the JVM runs on: its operating system and the directories applications use on it.
 *
 * <p>Use {@link #current()} to get the platform of the running JVM. Code that receives a {@code Platform} instead of
 * calling {@code current()} itself can be tested against any platform.
 *
 * <p>At most one of {@link #isLinux()}, {@link #isMacOS()} and {@link #isWindows()} is {@code true}. On other Unix-like
 * systems, such as FreeBSD, all three are {@code false}, and the directories follow the XDG Base Directory
 * Specification, as on Linux.
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
            this.systemProperties = Objects.requireNonNull(systemProperties, "systemProperties");
            return this;
        }

        /**
         * Sets the environment variables to read.
         *
         * @param environment the environment variables
         * @return this builder
         */
        public Builder environment(Environment environment) {
            this.environment = Objects.requireNonNull(environment, "environment");
            return this;
        }

        /**
         * Sets the file system the platform's paths belong to.
         *
         * @param fileSystem the file system
         * @return this builder
         */
        public Builder fileSystem(FileSystem fileSystem) {
            this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
            return this;
        }

        /**
         * Detects the platform from this builder's sources. The sources are read now; later changes to them do not
         * affect the platform.
         *
         * @return the platform
         * @throws java.util.NoSuchElementException if {@code os.name} or {@code user.home} is not set
         */
        public Platform detect() {
            return Platforms.detect(systemProperties, environment, fileSystem);
        }
    }
}
