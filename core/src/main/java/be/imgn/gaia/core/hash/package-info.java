/**
 * Mixing of bits, for code that needs numbers to look unrelated to the ones they come from: {@link
 * be.imgn.gaia.core.hash.Mixers} scrambles a number, and {@link be.imgn.gaia.core.hash.Feistel} scrambles the numbers
 * of a range among themselves, in a way that can be undone. Neither keeps a secret: they are fast, not
 * cryptographic.
 */
@NullMarked
package be.imgn.gaia.core.hash;

import org.jspecify.annotations.NullMarked;
