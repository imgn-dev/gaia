/**
 * A cross-platform API over the host the JVM runs on. Fakes of this API belong in {@code
 * be.imgn.gaia.platform.testing}.
 */
module be.imgn.gaia.platform {
    requires transitive be.imgn.gaia.core;
    requires transitive org.jspecify;

    exports be.imgn.gaia.platform;
}
