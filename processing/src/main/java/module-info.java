/**
 * Helpers for writing annotation processors.
 *
 * <p>This module is not a processor. A processor depends on it, and reaches its users on their annotation
 * processor path, so this module never lands on their runtime class path.
 */
module be.imgn.gaia.processing {
    requires be.imgn.gaia.core;
    requires transitive java.compiler;
    requires transitive org.jspecify;

    exports be.imgn.gaia.processing;
    exports be.imgn.gaia.processing.source;
}
