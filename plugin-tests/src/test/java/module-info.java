import be.imgn.gaia.plugin.Plugin;

/*
 * Tests of be.imgn.gaia.plugin, with plugins and applications that the tests compile while they run. Compiled as a
 * module, so they can use exported packages only, like any host.
 *
 * <p>Uses Plugin, to check that the host's own ServiceLoader lookups never find a plugin's providers.
 *
 * <p>Open, so that JUnit can reach every test package.
 */
open module be.imgn.gaia.plugin.tests {
    requires be.imgn.gaia.plugin;
    requires jdk.compiler;
    requires org.assertj.core;
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    uses Plugin;
}
