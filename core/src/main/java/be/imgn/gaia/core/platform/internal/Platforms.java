package be.imgn.gaia.core.platform.internal;

import be.imgn.gaia.core.platform.Platform;
import be.imgn.gaia.core.system.Environment;
import be.imgn.gaia.core.system.SystemProperties;
import java.nio.file.FileSystem;
import java.util.Locale;
import java.util.NoSuchElementException;

/** Detects the {@link Platform} the JVM runs on. */
public final class Platforms {

    private Platforms() {}

    /**
     * Returns the platform of the running JVM, detected once.
     *
     * @return the current platform
     */
    public static Platform current() {
        return Current.PLATFORM;
    }

    /**
     * Detects a platform; see {@link Platform.Builder}.
     *
     * @param properties the system properties
     * @param environment the environment variables
     * @param fileSystem the file system the platform's paths belong to
     * @return the platform
     */
    public static Platform detect(SystemProperties properties, Environment environment, FileSystem fileSystem) {
        // As text: the home directory is a path of the platform's file system, not of the default one.
        String userHome = properties
                .get("user.home")
                .orElseThrow(() -> new NoSuchElementException("System property user.home is not set"));
        String name = properties.osName().toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return new WindowsPlatform(fileSystem, new WindowsDirectories(fileSystem, userHome, environment));
        }
        if (name.startsWith("mac") || name.startsWith("darwin")) {
            return new MacOSPlatform(fileSystem, new MacOSDirectories(fileSystem, userHome));
        }
        XdgDirectories xdg = new XdgDirectories(fileSystem, userHome, environment);
        return name.startsWith("linux") ? new LinuxPlatform(fileSystem, xdg) : new UnixPlatform(fileSystem, xdg);
    }

    private static final class Current {

        static final Platform PLATFORM = Platform.newBuilder().detect();

        private Current() {}
    }
}
