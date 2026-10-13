package be.imgn.gaia.platform.testing;

import be.imgn.gaia.core.system.Environment;
import be.imgn.gaia.core.system.SystemProperties;
import be.imgn.gaia.platform.Platform;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The system properties and environment variables a JVM typically has on an operating system, to detect a
 * {@link Platform} from, so that tests run the real detection of the directories of any operating system:
 *
 * <pre>{@code
 * PlatformPreset windows = PlatformPreset.newBuilder(OperatingSystem.WINDOWS).userName("alice").build();
 * Platform platform = Platform.newBuilder()
 *         .systemProperties(windows.systemProperties())
 *         .environment(windows.environment())
 *         .fileSystem(fileSystem)
 *         .detect();
 * }</pre>
 *
 * <p>The user is {@code test} unless {@linkplain Builder#userName(String) given}, and their home directory is the
 * default one for that user, unless {@linkplain Builder#home(String) given}. With user {@code test}, the system
 * properties are:
 *
 * <table>
 *   <caption>System properties</caption>
 *   <tr><th>Property</th><th>Linux</th><th>Other Unix</th><th>macOS</th><th>Windows</th></tr>
 *   <tr><td>{@code os.name}</td><td>{@code Linux}</td><td>{@code FreeBSD}</td><td>{@code Mac OS X}</td>
 *       <td>{@code Windows 11}</td></tr>
 *   <tr><td>{@code os.version}</td><td>{@code 6.8.0}</td><td>{@code 14.1-RELEASE}</td><td>{@code 15.0}</td>
 *       <td>{@code 10.0}</td></tr>
 *   <tr><td>{@code os.arch}</td><td>{@code amd64}</td><td>{@code amd64}</td><td>{@code aarch64}</td>
 *       <td>{@code amd64}</td></tr>
 *   <tr><td>{@code user.name}</td><td colspan="4">{@code test}</td></tr>
 *   <tr><td>{@code user.home}, {@code user.dir}</td><td>{@code /home/test}</td><td>{@code /home/test}</td>
 *       <td>{@code /Users/test}</td><td>{@code C:\Users\test}</td></tr>
 *   <tr><td>{@code java.io.tmpdir}</td><td>{@code /tmp}</td><td>{@code /tmp}</td><td>{@code /tmp}</td>
 *       <td>{@code C:\Users\test\AppData\Local\Temp\}</td></tr>
 *   <tr><td>{@code file.separator}</td><td>{@code /}</td><td>{@code /}</td><td>{@code /}</td><td>{@code \}</td></tr>
 *   <tr><td>{@code path.separator}</td><td>{@code :}</td><td>{@code :}</td><td>{@code :}</td><td>{@code ;}</td></tr>
 *   <tr><td>{@code line.separator}</td><td>{@code \n}</td><td>{@code \n}</td><td>{@code \n}</td>
 *       <td>{@code \r\n}</td></tr>
 * </table>
 *
 * <p>The environment variables are:
 *
 * <ul>
 *   <li>Linux, other Unix, macOS: {@code HOME} (the home directory) and {@code USER} (the user name). No XDG variable
 *       is set, so the specification's defaults apply, unless {@link Builder#xdgVariables(boolean)} sets them.
 *   <li>Windows: {@code USERNAME}, {@code USERPROFILE} (the home directory), {@code APPDATA}
 *       ({@code C:\Users\test\AppData\Roaming}), {@code LOCALAPPDATA} ({@code C:\Users\test\AppData\Local}),
 *       {@code TEMP} and {@code TMP} ({@code C:\Users\test\AppData\Local\Temp}), {@code ProgramData}
 *       ({@code C:\ProgramData}), {@code SystemDrive} ({@code C:}) and {@code SystemRoot} ({@code C:\Windows}). Their
 *       names are case-sensitive, unlike on a real Windows.
 * </ul>
 *
 * <p>The values are strings, which the platform turns into paths of the file system it is given. Give it a file system
 * of the same kind as the operating system to get the same paths as on that system: on a Unix file system, for example,
 * a Windows path such as {@code C:\Users\test} is a relative path with a single name. The detected paths are still
 * consistent, so a test can compare them with paths it builds on the same file system, the same way.
 */
public final class PlatformPreset {

    private static final String DEFAULT_USER_NAME = "test";
    private static final String UNIX_TEMPORARY_DIRECTORY = "/tmp";
    private static final String HOME = "HOME";
    private static final String USER = "USER";

    private final SystemProperties systemProperties;
    private final Environment environment;

    private PlatformPreset(Map<String, String> systemProperties, Map<String, String> environment) {
        this.systemProperties = SystemProperties.of(systemProperties);
        this.environment = Environment.of(environment);
    }

    /**
     * Returns a builder of the system properties and environment variables of an operating system.
     *
     * @param operatingSystem the operating system
     * @return a new builder
     */
    public static Builder newBuilder(OperatingSystem operatingSystem) {
        return new Builder(operatingSystem);
    }

    /**
     * Returns the system properties, to give to {@link Platform.Builder#systemProperties(SystemProperties)}.
     *
     * @return the system properties
     */
    public SystemProperties systemProperties() {
        return systemProperties;
    }

    /**
     * Returns the environment variables, to give to {@link Platform.Builder#environment(Environment)}.
     *
     * @return the environment variables
     */
    public Environment environment() {
        return environment;
    }

    /**
     * Builds a {@link PlatformPreset}. The properties and variables set one by one are applied last, over the ones the
     * preset computes from the operating system, the user name and the home directory.
     */
    public static final class Builder {

        private final OperatingSystem operatingSystem;
        private final Map<String, String> systemProperties = new HashMap<>();
        private final Map<String, String> environmentVariables = new HashMap<>();
        private final Set<String> removedEnvironmentVariables = new HashSet<>();
        private String userName = DEFAULT_USER_NAME;
        private @Nullable String home;
        private boolean xdgVariables;

        private Builder(OperatingSystem operatingSystem) {
            this.operatingSystem = Objects.requireNonNull(operatingSystem, "operatingSystem");
        }

        /**
         * Sets the name of the user, {@code test} by default. Unless {@linkplain #home(String) given}, the home
         * directory is the default one for that user: {@code /home/}<i>name</i> on Linux and other Unix-like systems,
         * {@code /Users/}<i>name</i> on macOS, {@code C:\Users\}<i>name</i> on Windows.
         *
         * @param userName the user name
         * @return this builder
         * @throws IllegalArgumentException if {@code userName} is blank
         */
        public Builder userName(String userName) {
            this.userName = nonBlank(userName, "userName");
            return this;
        }

        /**
         * Sets the home directory of the user, which the other directories of the user, such as {@code APPDATA} on
         * Windows, are under.
         *
         * @param home the home directory, in the syntax of the operating system, such as {@code /home/alice} or
         *     {@code D:\Users\alice}
         * @return this builder
         * @throws IllegalArgumentException if {@code home} is blank
         */
        public Builder home(String home) {
            this.home = nonBlank(home, "home");
            return this;
        }

        /**
         * Sets whether to set every variable of the XDG Base Directory Specification, each to a location that differs
         * from the specification's default: {@code XDG_DATA_HOME=/xdg/data-home},
         * {@code XDG_CONFIG_HOME=/xdg/config-home}, {@code XDG_STATE_HOME=/xdg/state-home},
         * {@code XDG_CACHE_HOME=/xdg/cache-home}, {@code XDG_RUNTIME_DIR=/xdg/runtime-dir},
         * {@code XDG_DATA_DIRS=/xdg/data-dir/first:/xdg/data-dir/second} and
         * {@code XDG_CONFIG_DIRS=/xdg/config-dir/first:/xdg/config-dir/second}. By default none is set.
         *
         * <p>They can be set on any operating system, to check that only Linux and other Unix-like systems read them.
         *
         * @param xdgVariables {@code true} to set the XDG variables
         * @return this builder
         */
        public Builder xdgVariables(boolean xdgVariables) {
            this.xdgVariables = xdgVariables;
            return this;
        }

        /**
         * Sets a system property, replacing the preset's value if it has one.
         *
         * @param name the name of the property
         * @param value the value of the property
         * @return this builder
         */
        public Builder systemProperty(String name, String value) {
            systemProperties.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Sets an environment variable, replacing the preset's value if it has one.
         *
         * @param name the name of the variable
         * @param value the value of the variable
         * @return this builder
         */
        public Builder environmentVariable(String name, String value) {
            environmentVariables.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            removedEnvironmentVariables.remove(name);
            return this;
        }

        /**
         * Removes an environment variable, such as {@code HOME} or {@code APPDATA}, to test what happens when it is not
         * set.
         *
         * @param name the name of the variable
         * @return this builder
         */
        public Builder removeEnvironmentVariable(String name) {
            environmentVariables.remove(Objects.requireNonNull(name, "name"));
            removedEnvironmentVariables.add(name);
            return this;
        }

        /**
         * Builds the system properties and environment variables.
         *
         * @return the preset
         */
        public PlatformPreset build() {
            Map<String, String> properties = new HashMap<>();
            Map<String, String> environment = new HashMap<>();
            Host host = Host.of(operatingSystem);
            String userHome = Objects.requireNonNullElse(home, host.homeParent() + userName);
            properties.put("os.name", host.osName());
            properties.put("os.version", host.osVersion());
            properties.put("os.arch", host.osArch());
            properties.put("user.name", userName);
            properties.put("user.home", userHome);
            properties.put("user.dir", userHome);
            if (operatingSystem == OperatingSystem.WINDOWS) {
                windows(properties, environment, userHome);
            } else {
                unix(properties, environment, userHome);
            }
            if (xdgVariables) {
                environment.put("XDG_DATA_HOME", "/xdg/data-home");
                environment.put("XDG_CONFIG_HOME", "/xdg/config-home");
                environment.put("XDG_STATE_HOME", "/xdg/state-home");
                environment.put("XDG_CACHE_HOME", "/xdg/cache-home");
                environment.put("XDG_RUNTIME_DIR", "/xdg/runtime-dir");
                environment.put("XDG_DATA_DIRS", "/xdg/data-dir/first:/xdg/data-dir/second");
                environment.put("XDG_CONFIG_DIRS", "/xdg/config-dir/first:/xdg/config-dir/second");
            }
            properties.putAll(systemProperties);
            environment.putAll(environmentVariables);
            environment.keySet().removeAll(removedEnvironmentVariables);
            return new PlatformPreset(properties, environment);
        }

        private void unix(Map<String, String> properties, Map<String, String> environment, String userHome) {
            properties.put("java.io.tmpdir", UNIX_TEMPORARY_DIRECTORY);
            properties.put("file.separator", "/");
            properties.put("path.separator", ":");
            properties.put("line.separator", "\n");
            environment.put(HOME, userHome);
            environment.put(USER, userName);
        }

        private void windows(Map<String, String> properties, Map<String, String> environment, String userHome) {
            String localAppData = userHome + "\\AppData\\Local";
            String temporaryDirectory = localAppData + "\\Temp";
            properties.put("java.io.tmpdir", temporaryDirectory + "\\");
            properties.put("file.separator", "\\");
            properties.put("path.separator", ";");
            properties.put("line.separator", "\r\n");
            environment.put("USERNAME", userName);
            environment.put("USERPROFILE", userHome);
            environment.put("APPDATA", userHome + "\\AppData\\Roaming");
            environment.put("LOCALAPPDATA", localAppData);
            environment.put("TEMP", temporaryDirectory);
            environment.put("TMP", temporaryDirectory);
            environment.put("ProgramData", "C:\\ProgramData");
            environment.put("SystemDrive", "C:");
            environment.put("SystemRoot", "C:\\Windows");
        }

        private static String nonBlank(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }

        /** What differs between the operating systems, besides their environment variables. */
        private record Host(String osName, String osVersion, String osArch, String homeParent) {

            static Host of(OperatingSystem operatingSystem) {
                return switch (operatingSystem) {
                    case LINUX -> new Host("Linux", "6.8.0", "amd64", "/home/");
                    case OTHER_UNIX -> new Host("FreeBSD", "14.1-RELEASE", "amd64", "/home/");
                    case MAC_OS -> new Host("Mac OS X", "15.0", "aarch64", "/Users/");
                    case WINDOWS -> new Host("Windows 11", "10.0", "amd64", "C:\\Users\\");
                };
            }
        }
    }
}
