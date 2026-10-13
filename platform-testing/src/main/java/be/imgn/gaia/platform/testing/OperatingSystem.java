package be.imgn.gaia.platform.testing;

import be.imgn.gaia.platform.Platform;

/**
 * The operating systems a {@link Platform} can tell apart: one for each of {@link Platform#isLinux()},
 * {@link Platform#isMacOS()} and {@link Platform#isWindows()}, and one for the other Unix-like systems, for which all
 * three are {@code false}.
 */
public enum OperatingSystem {

    /** Linux. */
    LINUX,

    /** macOS. */
    MAC_OS,

    /** Windows. */
    WINDOWS,

    /** A Unix-like system other than Linux and macOS, such as FreeBSD. */
    OTHER_UNIX
}
