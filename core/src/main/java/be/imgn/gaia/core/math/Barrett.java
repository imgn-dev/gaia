package be.imgn.gaia.core.math;

/**
 * Multiplication modulo a modulus of at most {@code 2^32}, so that the product of two values less than it fits in an
 * unsigned {@code long}. A product is reduced with two multiplications and no division.
 */
final class Barrett {

    private final long modulus;
    // floor((2^64 - 1) / modulus): a value's quotient by the modulus is about the high half of its product by it.
    private final long reciprocal;

    /** Creates the arithmetic modulo a modulus between 1 and {@code 2^32}. */
    Barrett(long modulus) {
        this.modulus = modulus;
        reciprocal = Long.divideUnsigned(-1L, modulus);
    }

    /** Returns the product of two values between 0 and the modulus, excluded, modulo the modulus. */
    long multiply(long a, long b) {
        return reduce(a * b);
    }

    /** Returns a value between 0 and the modulus, excluded, to a non-negative power, modulo the modulus. */
    long pow(long base, long exponent) {
        var result = 1 % modulus;
        while (exponent != 0) {
            if ((exponent & 1) != 0) {
                result = multiply(result, base);
            }
            base = multiply(base, base);
            exponent >>>= 1;
        }
        return result;
    }

    // Returns x mod modulus, for an unsigned x.
    private long reduce(long x) {
        // The estimated quotient is the actual one or one less, so the remainder is less than twice the modulus.
        var remainder = x - Math.unsignedMultiplyHigh(x, reciprocal) * modulus;
        return remainder >= modulus ? remainder - modulus : remainder;
    }
}
