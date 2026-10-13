/*
 * Tests of {@code be.imgn.gaia.platform} through {@code be.imgn.gaia.platform.testing}. Compiled as a
 * module, so they can use exported packages only, like any user.
 *
 * <p>Open, so that JUnit can reach every test package, including ones that exist only on some JDKs.
 */
open module be.imgn.gaia.platform.tests {
    requires be.imgn.gaia.platform;
    requires be.imgn.gaia.platform.testing;
    requires org.assertj.core;
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;
}
