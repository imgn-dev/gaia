/**
 * Test helpers for code that uses {@code be.imgn.gaia.core}, such as a {@link be.imgn.gaia.core.testing.FakeTicker},
 * a {@link be.imgn.gaia.core.testing.FakePlatform}, or AssertJ assertions on its types.
 *
 * <p>AssertJ is required transitively: {@link be.imgn.gaia.core.testing.RangeAssert},
 * {@link be.imgn.gaia.core.testing.MultisetAssert} and {@link be.imgn.gaia.core.testing.MultimapAssert} extend AssertJ's
 * assertions.
 */
module be.imgn.gaia.core.testing {
    requires transitive be.imgn.gaia.core;
    requires transitive org.assertj.core;
    requires transitive org.jspecify;

    exports be.imgn.gaia.core.testing;
}
