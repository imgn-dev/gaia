/**
 * Fakes of the {@code be.imgn.gaia.platform} API, such as a {@link be.imgn.gaia.platform.testing.FakePlatform}. Plain
 * Java: usable from any test framework.
 */
module be.imgn.gaia.platform.testing {
    requires transitive be.imgn.gaia.platform;
    requires transitive org.jspecify;

    exports be.imgn.gaia.platform.testing;
}
