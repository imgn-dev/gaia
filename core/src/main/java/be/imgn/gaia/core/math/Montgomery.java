package be.imgn.gaia.core.math;

/**
 * Multiplication modulo an odd modulus, on values in Montgomery form: {@code x} is held as {@code x * 2^64 mod
 * modulus}. A product is then reduced with two multiplications and no division.
 */
final class Montgomery {

    private final long modulus;
    // -modulus^-1 mod 2^64.
    private final long negatedInverse;
    // 2^64 mod modulus: 1 in Montgomery form.
    private final long one;
    // 2^128 mod modulus, which turns a value into Montgomery form in one multiplication.
    private final long rSquared;

    /** Creates the arithmetic modulo an odd modulus greater than one and less than {@code 2^63}. */
    Montgomery(long modulus) {
        this.modulus = modulus;
        negatedInverse = -inverse(modulus);
        // 2^64 - modulus is 2^64 mod modulus, plus a multiple of it.
        one = Long.remainderUnsigned(-modulus, modulus);
        // Doublings make 2^8 in Montgomery form, and squarings 2^16, 2^32, then 2^64 in Montgomery form: 2^128 mod
        // modulus. A squaring costs more than a doubling, but replaces many.
        var r = one;
        for (int i = 0; i < Byte.SIZE; i++) {
            r = add(r, r);
        }
        r = multiply(r, r);
        r = multiply(r, r);
        rSquared = multiply(r, r);
    }

    /** Returns the inverse of an odd value modulo {@code 2^64}. */
    static long inverse(long odd) {
        // An odd value is its own inverse modulo 2^3, and each Newton step doubles the correct bits: 6, 12, 24, 48, 96.
        var x = odd;
        x *= 2 - odd * x;
        x *= 2 - odd * x;
        x *= 2 - odd * x;
        x *= 2 - odd * x;
        x *= 2 - odd * x;
        return x;
    }

    /** Returns 1 in Montgomery form. */
    long one() {
        return one;
    }

    /** Returns -1 in Montgomery form. */
    long minusOne() {
        return modulus - one;
    }

    /** Returns a value between 0 and the modulus, excluded, in Montgomery form. */
    long toMontgomery(long x) {
        return multiply(x, rSquared);
    }

    /** Returns a value in Montgomery form as a value between 0 and the modulus, excluded. */
    long fromMontgomery(long x) {
        return reduce(0, x);
    }

    /** Returns the product of two values in Montgomery form, in Montgomery form. */
    long multiply(long a, long b) {
        return reduce(Math.unsignedMultiplyHigh(a, b), a * b);
    }

    /** Returns a value in Montgomery form to a non-negative power, in Montgomery form. */
    long pow(long base, long exponent) {
        var result = one;
        while (exponent != 0) {
            if ((exponent & 1) != 0) {
                result = multiply(result, base);
            }
            base = multiply(base, base);
            exponent >>>= 1;
        }
        return result;
    }

    // Returns (high * 2^64 + low) / 2^64 mod modulus, for a dividend less than modulus * 2^64.
    private long reduce(long high, long low) {
        var m = low * negatedInverse;
        // low + m * modulus is a multiple of 2^64: its low halves add up to 2^64, which carries one, unless low is 0.
        var t = high + Math.unsignedMultiplyHigh(m, modulus) + (low != 0 ? 1 : 0);
        return Long.compareUnsigned(t, modulus) >= 0 ? t - modulus : t;
    }

    // Both are less than the modulus, so less than 2^63, and their sum fits as an unsigned long.
    private long add(long a, long b) {
        var sum = a + b;
        return Long.compareUnsigned(sum, modulus) >= 0 ? sum - modulus : sum;
    }
}
