/*
 * Tests of {@code be.imgn.gaia.core} through {@code be.imgn.gaia.core.testing}. Compiled as a module,
 * so they can use exported packages only, like any user.
 *
 * <p>Open, so that JUnit can reach every test package, including ones that exist only on some JDKs.
 */
open module be.imgn.gaia.core.tests {
    requires be.imgn.gaia.core;
    requires be.imgn.gaia.core.testing;
    requires org.assertj.core;
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;
}
