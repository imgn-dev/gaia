package be.imgn.gaia.core.hash;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;

/**
 * A Feistel network: a way to scramble the numbers of a given number of bits among themselves, that can be undone.
 * Every number gives a different one, so that the numbers from zero to a power of two come out in another order, which
 * the keys decide.
 *
 * <pre>{@code
 * var network = Feistel.of(32, key1, key2, key3, key4, key5, key6);
 * long scrambled = network.apply(42);          // some number below 2^32
 * network.invert(scrambled);                   // 42
 * }</pre>
 *
 * <p>It makes consecutive numbers, such as identifiers, look scattered, and gives them back; or visits a range in an
 * order that looks random without storing it, as {@link be.imgn.gaia.core.random.RandomPermutation} does.
 *
 * <p>The number is split in two halves. Each round mixes one half, with a key, into the other one, then swaps them:
 * since the half that is mixed in stays as it is, the round can be undone, whatever the mixing does. The mixing is
 * {@link Mixers#mix64(long)}.
 *
 * <p>How random the result looks depends on the numbers of bits and of rounds. Six rounds or more are advised. With
 * very few bits, eight or so, the halves hold too few values to hide the structure, whatever the number of rounds:
 * shuffle such a small range instead. It isn't cryptographic: someone who sees enough results finds the others.
 *
 * <p>Networks are immutable.
 *
 * @since 0.2.0
 */
public final class Feistel {

    private static final int MIN_BITS = 2;
    private static final int MAX_BITS = Long.SIZE - 1;

    private final int bits;
    private final long[] keys;

    private Feistel(int bits, long[] keys) {
        this.bits = bits;
        this.keys = keys;
    }

    /**
     * Returns a network on the numbers of a given number of bits, with one round per key.
     *
     * @param bits the number of bits, from 2 to 63: the network scrambles the numbers from zero to below {@code 2^bits}
     * @param keys the key of each round, in order
     * @return the network
     * @throws IllegalArgumentException if {@code bits} is less than 2 or more than 63, or if there is no key
     */
    public static Feistel of(int bits, long... keys) {
        checkArgument(bits >= MIN_BITS && bits <= MAX_BITS, "bits must be from 2 to 63: %d", bits);
        checkArgument(keys.length > 0, "A network has at least one round");
        return new Feistel(bits, keys.clone());
    }

    /**
     * Returns the number of bits of the numbers that this network scrambles.
     *
     * @return the number of bits
     */
    public int bits() {
        return bits;
    }

    /**
     * Returns the number of rounds of this network.
     *
     * @return the number of rounds
     */
    public int rounds() {
        return keys.length;
    }

    /**
     * Scrambles a number.
     *
     * @param value a number from zero to below {@code 2^bits}
     * @return another number of that range, that no other value gives
     * @throws IllegalArgumentException if {@code value} is out of range
     */
    public long apply(long value) {
        checkRange(value);
        // The halves differ by one bit when the number of bits is odd: each round swaps their sizes too.
        int leftBits = bits / 2;
        int rightBits = bits - leftBits;
        long left = value >>> rightBits;
        long right = value & mask(rightBits);
        for (long key : keys) {
            long mixed = left ^ (Mixers.mix64(right + key) & mask(leftBits));
            left = right;
            right = mixed;
            int swapped = leftBits;
            leftBits = rightBits;
            rightBits = swapped;
        }
        return left << rightBits | right;
    }

    /**
     * Returns the number that {@link #apply(long)} scrambles to a given one.
     *
     * @param value a number from zero to below {@code 2^bits}
     * @return the number {@code original} such that {@code apply(original) == value}
     * @throws IllegalArgumentException if {@code value} is out of range
     */
    public long invert(long value) {
        checkRange(value);
        // The sizes of the halves after the last round: swapped if the number of rounds is odd.
        int leftBits = bits / 2;
        int rightBits = bits - leftBits;
        if (keys.length % 2 != 0) {
            leftBits = rightBits;
            rightBits = bits - leftBits;
        }
        long left = value >>> rightBits;
        long right = value & mask(rightBits);
        for (int round = keys.length - 1; round >= 0; round--) {
            long mixed = right ^ (Mixers.mix64(left + keys[round]) & mask(rightBits));
            right = left;
            left = mixed;
            int swapped = leftBits;
            leftBits = rightBits;
            rightBits = swapped;
        }
        return left << rightBits | right;
    }

    private void checkRange(long value) {
        checkArgument(value >= 0 && value >>> bits == 0, "Not a number of %d bits: %d", bits, value);
    }

    private static long mask(int bits) {
        return (1L << bits) - 1;
    }
}
