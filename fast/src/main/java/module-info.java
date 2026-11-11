/**
 * Fast collections of primitives, which store them without boxing. See
 * {@link be.imgn.gaia.fast.collect.IntIntMap}.
 */
module be.imgn.gaia.fast {
    requires be.imgn.gaia.core;
    requires transitive org.jspecify;

    exports be.imgn.gaia.fast.collect;
}
