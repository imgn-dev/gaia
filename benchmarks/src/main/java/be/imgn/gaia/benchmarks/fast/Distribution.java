package be.imgn.gaia.benchmarks.fast;

/** How the keys of a benchmark are picked. */
public enum Distribution {
    /** Distinct random keys. */
    RANDOM,
    /** The keys from 1 to the size: identifiers, indices. */
    SEQUENTIAL
}
