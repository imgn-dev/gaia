/**
 * Randomness, with the source of it always given as an argument. {@link be.imgn.gaia.core.random.RandomSelector}
 * selects one element at a time, uniformly or by weight, and {@link be.imgn.gaia.core.random.MutableRandomSelector}
 * does when the weights change. {@link be.imgn.gaia.core.random.RandomTable} draws several elements at once, some of
 * them from other tables. {@link be.imgn.gaia.core.random.RandomBag} and {@link
 * be.imgn.gaia.core.random.RandomSamples} take elements without putting them back. {@link
 * be.imgn.gaia.core.random.RandomPermutation} visits numbers in a random order. {@link
 * be.imgn.gaia.core.random.Chances} are the exact chances of what selectors and tables give. {@link
 * be.imgn.gaia.core.random.Seed} derives reproducible seeds, and {@link be.imgn.gaia.core.random.RandomStrings} makes
 * identifiers and tokens.
 */
@NullMarked
package be.imgn.gaia.core.random;

import org.jspecify.annotations.NullMarked;
