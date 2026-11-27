package be.imgn.gaia.core.hash;

/**
 * Functions that mix the bits of a number, so that numbers that are close, such as the ones of a counter, give numbers
 * that look unrelated.
 *
 * <pre>{@code
 * Mixers.mix64(1);    // 6238072747940578789
 * Mixers.mix64(2);    // -2606959012126976886
 * }</pre>
 *
 * <p>They are for hash tables, seeds and the like. They aren't cryptographic: what was mixed is easily found back.
 *
 * @since 0.2.0
 */
public final class Mixers {

    /**
     * The odd number closest to 2^64 divided by the golden ratio. Adding it over and over visits the 64-bit numbers in
     * an order that spreads them evenly, which makes it the usual step between the numbers given to
     * {@link #mix64(long)}.
     */
    public static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    // The multipliers and shifts of the variant 13 of David Stafford's mixing functions.
    private static final long MULTIPLIER_1 = 0xBF58476D1CE4E5B9L;
    private static final long MULTIPLIER_2 = 0x94D049BB133111EBL;
    private static final int SHIFT_1 = 30;
    private static final int SHIFT_2 = 27;
    private static final int SHIFT_3 = 31;

    private Mixers() {}

    /**
     * Mixes the 64 bits of a number: each bit of the result depends on every bit of the number, and changing one bit of
     * the number changes about half of the bits of the result. Different numbers give different results, and zero gives
     * zero.
     *
     * <p>This is the function that {@link java.util.SplittableRandom} mixes its state with. Its results are part of its
     * contract: they are the same in every version and on every JVM.
     *
     * @param value the number to mix
     * @return the mixed number
     */
    public static long mix64(long value) {
        long z = value;
        z = (z ^ (z >>> SHIFT_1)) * MULTIPLIER_1;
        z = (z ^ (z >>> SHIFT_2)) * MULTIPLIER_2;
        return z ^ (z >>> SHIFT_3);
    }
}
