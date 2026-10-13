/**
 * Extensions to the JDK.
 *
 * <p>JSpecify is required transitively, not statically: its nullness annotations are part of the
 * signatures this module exports, so every module that reads this one must be able to read them too.
 */
module be.imgn.gaia.core {
    requires transitive org.jspecify;

    exports be.imgn.gaia.core.collect;
    exports be.imgn.gaia.core.io;
    exports be.imgn.gaia.core.system;
    exports be.imgn.gaia.core.text;
    exports be.imgn.gaia.core.time;
}
