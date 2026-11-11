/**
 * JMH benchmarks of the gaia-fast collections, each next to the same operations on {@link java.util.HashMap}.
 */
// Not @NullMarked: with the JMH processor running, javac's "processing" lint reports annotations that no processor
// claims, fatal under -Werror. NullAway checks this package anyway, as it checks every package under be.imgn.
package be.imgn.gaia.benchmarks.fast;
