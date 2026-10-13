/**
 * Test helpers for code that uses {@code be.imgn.gaia.core}, such as a {@link be.imgn.gaia.core.testing.FakeTicker}.
 */
module be.imgn.gaia.core.testing {
    requires transitive be.imgn.gaia.core;
    requires transitive org.jspecify;

    exports be.imgn.gaia.core.testing;
}
