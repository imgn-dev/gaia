package be.imgn.gaia.core.math;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.math.RoundingMode;

/**
 * Arithmetic on {@code long} values that {@link Math} doesn't provide: divisors, powers, logarithms, square roots and
 * divisions with a rounding mode, saturated arithmetic, combinatorics and primality.
 *
 * <pre>{@code
 * LongMath.gcd(-12, 18)                                 // 6
 * LongMath.log10(1_000_000_000_000L, RoundingMode.DOWN)  // 12
 * LongMath.sqrt(Long.MAX_VALUE, RoundingMode.FLOOR)      // 3037000499
 * LongMath.saturatedMultiply(Long.MAX_VALUE, -2)         // Long.MIN_VALUE
 * LongMath.isPrime(Long.MAX_VALUE - 24)                  // true
 * }</pre>
 *
 * <p>A method whose result may not fit in a {@code long} throws {@link ArithmeticException} when it doesn't, except
 * {@link #pow(long, int)}, which wraps around like {@code long} multiplication, the {@code saturated} methods, which
 * return {@link Long#MAX_VALUE} or {@link Long#MIN_VALUE} instead, and {@link #divide(long, long, RoundingMode)}, which
 * overflows like {@link Math#floorDiv(long, long)}.
 *
 * @since 0.1.3
 * @see IntMath
 */
public final class LongMath {

    // floor(2^63.5): the integer part of the midpoint between 2^63 and 2^64, in logarithmic scale.
    private static final long FLOOR_SQRT_2_POW_63 = 0xB504_F333_F9DE_6484L;

    // log10(2) is about LOG10_2_NUMERATOR / 2^LOG10_2_SHIFT.
    private static final int LOG10_2_NUMERATOR = 1233;
    private static final int LOG10_2_SHIFT = 12;

    private static final long[] POWERS_OF_10 = {
        1L,
        10L,
        100L,
        1_000L,
        10_000L,
        100_000L,
        1_000_000L,
        10_000_000L,
        100_000_000L,
        1_000_000_000L,
        10_000_000_000L,
        100_000_000_000L,
        1_000_000_000_000L,
        10_000_000_000_000L,
        100_000_000_000_000L,
        1_000_000_000_000_000L,
        10_000_000_000_000_000L,
        100_000_000_000_000_000L,
        1_000_000_000_000_000_000L
    };

    // floor(10^(k + 1/2)) at index k.
    private static final long[] HALF_POWERS_OF_10 = {
        3L,
        31L,
        316L,
        3_162L,
        31_622L,
        316_227L,
        3_162_277L,
        31_622_776L,
        316_227_766L,
        3_162_277_660L,
        31_622_776_601L,
        316_227_766_016L,
        3_162_277_660_168L,
        31_622_776_601_683L,
        316_227_766_016_837L,
        3_162_277_660_168_379L,
        31_622_776_601_683_793L,
        316_227_766_016_837_933L,
        3_162_277_660_168_379_331L
    };

    private static final long[] FACTORIALS = {
        1L,
        1L,
        2L,
        6L,
        24L,
        120L,
        720L,
        5_040L,
        40_320L,
        362_880L,
        3_628_800L,
        39_916_800L,
        479_001_600L,
        6_227_020_800L,
        87_178_291_200L,
        1_307_674_368_000L,
        20_922_789_888_000L,
        355_687_428_096_000L,
        6_402_373_705_728_000L,
        121_645_100_408_832_000L,
        2_432_902_008_176_640_000L
    };

    // The Miller-Rabin test with these bases is never wrong below 2^64.
    private static final long[] PRIME_BASES = {2, 325, 9_375, 28_178, 450_775, 9_780_504, 1_795_265_022};

    private LongMath() {}

    /**
     * Returns the greatest common divisor of two values: the largest positive integer that divides both, or 0 if both
     * are 0. Signs don't matter: {@code gcd(-12, 18)} is 6.
     *
     * @param a a value
     * @param b another value
     * @return the greatest common divisor of {@code a} and {@code b}, never negative
     * @throws ArithmeticException if the result, {@code 2^63}, doesn't fit in a {@code long}: when {@code a} and
     *     {@code b} are each {@link Long#MIN_VALUE} or 0, but not both 0
     */
    public static long gcd(long a, long b) {
        if (a == 0 || b == 0) {
            return checkedAbs(a | b);
        }
        // Binary GCD, which only subtracts and shifts: a division takes longer. Both values stay odd, and the shift of
        // their difference is counted while their minimum is taken, so that the steps don't wait on each other.
        // Unsigned shifts take Long.MIN_VALUE, its own absolute value, as 2^63.
        var aTwos = Long.numberOfTrailingZeros(a);
        var bTwos = Long.numberOfTrailingZeros(b);
        var u = Math.abs(a) >>> aTwos;
        var v = Math.abs(b);
        var vTwos = bTwos;
        while (true) {
            v >>>= vTwos;
            var difference = v - u;
            if (difference == 0) {
                return checkedAbs(u << Math.min(aTwos, bTwos));
            }
            vTwos = Long.numberOfTrailingZeros(difference);
            u = Math.min(u, v);
            v = Math.abs(difference);
        }
    }

    /**
     * Returns the least common multiple of two values: the smallest positive integer that both divide, or 0 if either
     * is 0. Signs don't matter: {@code lcmExact(-4, 6)} is 12.
     *
     * @param a a value
     * @param b another value
     * @return the least common multiple of {@code a} and {@code b}, never negative
     * @throws ArithmeticException if the result doesn't fit in a {@code long}
     */
    public static long lcmExact(long a, long b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return checkedAbs(Math.multiplyExact(a / gcd(a, b), b));
    }

    /**
     * Returns whether a value is a power of two: 1, 2, 4, 8 and so on.
     *
     * @param x the value
     * @return whether {@code x} is a power of two, false for 0 and negative values
     */
    public static boolean isPowerOfTwo(long x) {
        return x > 0 && (x & (x - 1)) == 0;
    }

    /**
     * Returns the largest power of two that doesn't exceed a value.
     *
     * @param x the value
     * @return the largest power of two not above {@code x}
     * @throws IllegalArgumentException if {@code x} isn't positive
     */
    public static long floorPowerOfTwo(long x) {
        checkPositive("x", x);
        return Long.highestOneBit(x);
    }

    /**
     * Returns the smallest power of two that isn't less than a value.
     *
     * @param x the value
     * @return the smallest power of two not below {@code x}
     * @throws IllegalArgumentException if {@code x} isn't positive
     * @throws ArithmeticException if the result doesn't fit in a {@code long}: when {@code x} exceeds {@code 2^62}
     */
    public static long ceilingPowerOfTwo(long x) {
        checkPositive("x", x);
        if (x > 1L << (Long.SIZE - 2)) {
            throw new ArithmeticException("long overflow");
        }
        return 1L << -Long.numberOfLeadingZeros(x - 1);
    }

    /**
     * Returns the base-2 logarithm of a value, rounded to an integer.
     *
     * <p>A logarithm is never halfway between two integers unless it is an integer, so the {@code HALF_} modes all
     * round alike.
     *
     * @param x the value
     * @param mode how to round the logarithm
     * @return the base-2 logarithm of {@code x}, rounded with {@code mode}
     * @throws IllegalArgumentException if {@code x} isn't positive
     * @throws ArithmeticException if {@code mode} is {@link RoundingMode#UNNECESSARY} and {@code x} isn't a power of
     *     two
     */
    public static int log2(long x, RoundingMode mode) {
        checkPositive("x", x);
        requireNonNull(mode, "mode");
        var leadingZeros = Long.numberOfLeadingZeros(x);
        var floor = Long.SIZE - 1 - leadingZeros;
        if (isPowerOfTwo(x)) {
            return floor;
        }
        // The midpoint, 2^(floor + 1/2), is irrational: x is above it if it is above its integer part.
        var aboveHalf = x > FLOOR_SQRT_2_POW_63 >>> leadingZeros;
        return floor + (Rounding.awayFromZero(mode, false, aboveHalf ? 1 : -1, false) ? 1 : 0);
    }

    /**
     * Returns the base-10 logarithm of a value, rounded to an integer.
     *
     * <p>A logarithm is never halfway between two integers unless it is an integer, so the {@code HALF_} modes all
     * round alike.
     *
     * @param x the value
     * @param mode how to round the logarithm
     * @return the base-10 logarithm of {@code x}, rounded with {@code mode}
     * @throws IllegalArgumentException if {@code x} isn't positive
     * @throws ArithmeticException if {@code mode} is {@link RoundingMode#UNNECESSARY} and {@code x} isn't a power of
     *     ten
     */
    public static int log10(long x, RoundingMode mode) {
        checkPositive("x", x);
        requireNonNull(mode, "mode");
        // The result or one more, for every bit length up to 63.
        var guess = ((Long.SIZE - Long.numberOfLeadingZeros(x)) * LOG10_2_NUMERATOR) >>> LOG10_2_SHIFT;
        var floor = x < POWERS_OF_10[guess] ? guess - 1 : guess;
        if (x == POWERS_OF_10[floor]) {
            return floor;
        }
        // The midpoint, 10^(floor + 1/2), is irrational: x is above it if it is above its integer part.
        var aboveHalf = x > HALF_POWERS_OF_10[floor];
        return floor + (Rounding.awayFromZero(mode, false, aboveHalf ? 1 : -1, false) ? 1 : 0);
    }

    /**
     * Returns the square root of a value, rounded to an integer.
     *
     * <p>The square root of an integer is never halfway between two integers, so the {@code HALF_} modes all round
     * alike.
     *
     * @param x the value
     * @param mode how to round the square root
     * @return the square root of {@code x}, rounded with {@code mode}
     * @throws IllegalArgumentException if {@code x} is negative
     * @throws ArithmeticException if {@code mode} is {@link RoundingMode#UNNECESSARY} and {@code x} isn't a perfect
     *     square
     */
    public static long sqrt(long x, RoundingMode mode) {
        checkArgument(x >= 0, "x must not be negative: %s", x);
        requireNonNull(mode, "mode");
        // A double rounds x, so this may be one off near a perfect square. It never exceeds 3,037,000,499, whose
        // square fits.
        var floor = (long) Math.sqrt((double) x);
        var remainder = x - floor * floor;
        if (remainder < 0) {
            floor--;
            remainder = x - floor * floor;
        } else if (remainder > 2 * floor) {
            floor++;
            remainder = x - floor * floor;
        }
        if (remainder == 0) {
            return floor;
        }
        // The midpoint squared, floor^2 + floor + 1/4, isn't an integer: x is above it if it is above floor^2 + floor.
        var aboveHalf = remainder > floor;
        return floor + (Rounding.awayFromZero(mode, false, aboveHalf ? 1 : -1, false) ? 1 : 0);
    }

    /**
     * Returns the quotient of two values, rounded to an integer.
     *
     * <p>{@code divide(p, q, RoundingMode.DOWN)} is {@code p / q}, and {@code divide(p, q, RoundingMode.FLOOR)} is
     * {@link Math#floorDiv(long, long)}. Like them, it returns {@link Long#MIN_VALUE} for {@code Long.MIN_VALUE / -1},
     * which overflows.
     *
     * @param p the dividend
     * @param q the divisor
     * @param mode how to round the quotient
     * @return the quotient of {@code p} by {@code q}, rounded with {@code mode}
     * @throws ArithmeticException if {@code q} is 0, or if {@code mode} is {@link RoundingMode#UNNECESSARY} and
     *     {@code q} doesn't divide {@code p}
     */
    public static long divide(long p, long q, RoundingMode mode) {
        requireNonNull(mode, "mode");
        var quotient = p / q;
        var remainder = p - q * quotient;
        if (remainder == 0) {
            return quotient;
        }
        var negative = (p ^ q) < 0;
        // |remainder| against |q| - |remainder|, unsigned so that |Long.MIN_VALUE| counts as 2^63.
        var absRemainder = Math.abs(remainder);
        var halfComparison = Long.compareUnsigned(absRemainder, Math.abs(q) - absRemainder);
        if (Rounding.awayFromZero(mode, negative, halfComparison, (quotient & 1) != 0)) {
            return negative ? quotient - 1 : quotient + 1;
        }
        return quotient;
    }

    /**
     * Returns a value to a power, wrapping around on overflow like {@code long} multiplication.
     *
     * @param base the base
     * @param exponent the exponent
     * @return {@code base} to the power of {@code exponent}, keeping only its 64 lowest bits
     * @throws IllegalArgumentException if {@code exponent} is negative
     */
    public static long pow(long base, int exponent) {
        checkNotNegative("exponent", exponent);
        return wrappingPow(base, exponent);
    }

    /**
     * Returns a value to a power.
     *
     * @param base the base
     * @param exponent the exponent
     * @return {@code base} to the power of {@code exponent}
     * @throws IllegalArgumentException if {@code exponent} is negative
     * @throws ArithmeticException if the result doesn't fit in a {@code long}
     */
    public static long powExact(long base, int exponent) {
        checkNotNegative("exponent", exponent);
        var result = 1L;
        while (true) {
            if ((exponent & 1) != 0) {
                result = Math.multiplyExact(result, base);
            }
            exponent >>>= 1;
            if (exponent == 0) {
                return result;
            }
            // A factor of the result, which overflows too if this does.
            base = Math.multiplyExact(base, base);
        }
    }

    /**
     * Returns the sum of two values, or the {@code long} closest to it if it doesn't fit.
     *
     * @param a a value
     * @param b another value
     * @return {@code a + b}, clamped between {@link Long#MIN_VALUE} and {@link Long#MAX_VALUE}
     */
    public static long saturatedAdd(long a, long b) {
        var sum = a + b;
        // An overflowing sum has the opposite sign of both values.
        if (((a ^ sum) & (b ^ sum)) < 0) {
            return sum < 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return sum;
    }

    /**
     * Returns the difference of two values, or the {@code long} closest to it if it doesn't fit.
     *
     * @param a a value
     * @param b the value to subtract from {@code a}
     * @return {@code a - b}, clamped between {@link Long#MIN_VALUE} and {@link Long#MAX_VALUE}
     */
    public static long saturatedSubtract(long a, long b) {
        var difference = a - b;
        // An overflowing difference has the opposite sign of a, whose sign b doesn't have.
        if (((a ^ b) & (a ^ difference)) < 0) {
            return difference < 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return difference;
    }

    /**
     * Returns the product of two values, or the {@code long} closest to it if it doesn't fit.
     *
     * @param a a value
     * @param b another value
     * @return {@code a * b}, clamped between {@link Long#MIN_VALUE} and {@link Long#MAX_VALUE}
     */
    public static long saturatedMultiply(long a, long b) {
        var high = Math.multiplyHigh(a, b);
        var low = a * b;
        // The 128-bit product fits if its high half only extends the sign of its low half.
        if (high != low >> (Long.SIZE - 1)) {
            return (a ^ b) < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return low;
    }

    /**
     * Returns a value to a power, or the {@code long} closest to it if it doesn't fit.
     *
     * @param base the base
     * @param exponent the exponent
     * @return {@code base} to the power of {@code exponent}, clamped between {@link Long#MIN_VALUE} and
     *     {@link Long#MAX_VALUE}
     * @throws IllegalArgumentException if {@code exponent} is negative
     */
    public static long saturatedPow(long base, int exponent) {
        checkNotNegative("exponent", exponent);
        var limit = base < 0 && (exponent & 1) != 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        var result = 1L;
        while (true) {
            if ((exponent & 1) != 0) {
                var high = Math.multiplyHigh(result, base);
                result *= base;
                if (high != result >> (Long.SIZE - 1)) {
                    return limit;
                }
            }
            exponent >>>= 1;
            if (exponent == 0) {
                return result;
            }
            // A factor of the result, which overflows too if this does.
            var high = Math.multiplyHigh(base, base);
            base *= base;
            if (high != 0 || base < 0) {
                return limit;
            }
        }
    }

    /**
     * Returns a value to a power, modulo a modulus.
     *
     * @param base the base, which may be negative
     * @param exponent the exponent
     * @param modulus the modulus
     * @return {@code base} to the power of {@code exponent}, modulo {@code modulus}: between 0 and {@code modulus},
     *     excluded
     * @throws IllegalArgumentException if {@code exponent} is negative, or if {@code modulus} isn't positive
     */
    public static long powMod(long base, long exponent, long modulus) {
        checkNotNegative("exponent", exponent);
        checkPositive("modulus", modulus);
        // Montgomery multiplication needs an odd modulus. Modulo the power of two an even modulus has, wrapping
        // arithmetic gives the result, and the Chinese remainder theorem combines both.
        var twos = Long.numberOfTrailingZeros(modulus);
        var odd = modulus >> twos;
        var oddResult = 0L;
        if (odd != 1) {
            var montgomery = new Montgomery(odd);
            oddResult = montgomery.fromMontgomery(
                    montgomery.pow(montgomery.toMontgomery(Math.floorMod(base, odd)), exponent));
        }
        if (twos == 0) {
            return oddResult;
        }
        var mask = (1L << twos) - 1;
        var twosResult = wrappingPow(base, exponent) & mask;
        // The value between 0 and the modulus, excluded, that is oddResult modulo odd and twosResult modulo 2^twos.
        return oddResult + odd * (((twosResult - oddResult) * Montgomery.inverse(odd)) & mask);
    }

    /**
     * Returns the mean of two values, rounded toward negative infinity. Unlike {@code (a + b) / 2}, it never overflows.
     *
     * @param a a value
     * @param b another value
     * @return the mean of {@code a} and {@code b}, rounded toward negative infinity
     */
    public static long mean(long a, long b) {
        // The bits both have, plus half the bits only one has.
        return (a & b) + ((a ^ b) >> 1);
    }

    /**
     * Returns the factorial of a value: the product of the integers from 1 to it.
     *
     * @param n the value
     * @return {@code n!}, which is 1 for 0
     * @throws IllegalArgumentException if {@code n} is negative
     * @throws ArithmeticException if the result doesn't fit in a {@code long}: when {@code n} exceeds 20
     */
    public static long factorial(int n) {
        checkNotNegative("n", n);
        if (n >= FACTORIALS.length) {
            throw new ArithmeticException("long overflow");
        }
        return FACTORIALS[n];
    }

    /**
     * Returns a binomial coefficient: the number of ways to choose {@code k} elements among {@code n}.
     *
     * @param n the number of elements
     * @param k the number of elements to choose
     * @return {@code n! / (k! (n - k)!)}
     * @throws IllegalArgumentException if {@code k} is negative or exceeds {@code n}
     * @throws ArithmeticException if the result doesn't fit in a {@code long}
     */
    public static long binomial(long n, long k) {
        checkNotNegative("k", k);
        checkArgument(k <= n, "k must not exceed n: %s > %s", k, n);
        k = Math.min(k, n - k);
        var result = 1L;
        for (long i = 1; i <= k; i++) {
            // binomial(n - k + i, i), which grows with i up to the result: at least twice as large on each step, so
            // the loop ends soon when the result overflows. Dividing first by what result and i have in common keeps
            // the product from overflowing unless the result does.
            var common = gcd(result, i);
            result = Math.multiplyExact(result / common, (n - k + i) / (i / common));
        }
        return result;
    }

    /**
     * Returns whether a value is a prime number: an integer greater than 1 that only 1 and itself divide.
     *
     * <p>The answer is always exact.
     *
     * @param n the value
     * @return whether {@code n} is prime, false for values less than 2
     */
    public static boolean isPrime(long n) {
        if (n <= Integer.MAX_VALUE) {
            return n > 1 && IntMath.isPrime((int) n);
        }
        if ((n & 1) == 0) {
            return false;
        }
        var montgomery = new Montgomery(n);
        var s = Long.numberOfTrailingZeros(n - 1);
        var d = (n - 1) >>> s;
        for (var base : PRIME_BASES) {
            if (!isStrongProbablePrime(montgomery, base, d, s)) {
                return false;
            }
        }
        return true;
    }

    // For an odd modulus n = d * 2^s + 1, and a base less than n.
    private static boolean isStrongProbablePrime(Montgomery montgomery, long base, long d, int s) {
        var x = montgomery.pow(montgomery.toMontgomery(base), d);
        if (x == montgomery.one() || x == montgomery.minusOne()) {
            return true;
        }
        for (int i = 1; i < s; i++) {
            x = montgomery.multiply(x, x);
            if (x == montgomery.minusOne()) {
                return true;
            }
        }
        return false;
    }

    private static long wrappingPow(long base, long exponent) {
        var result = 1L;
        while (exponent != 0) {
            if ((exponent & 1) != 0) {
                result *= base;
            }
            base *= base;
            exponent >>>= 1;
        }
        return result;
    }

    private static long checkedAbs(long x) {
        if (x == Long.MIN_VALUE) {
            throw new ArithmeticException("long overflow");
        }
        return Math.abs(x);
    }

    private static void checkPositive(String name, long value) {
        checkArgument(value > 0, "%s must be positive: %s", name, value);
    }

    private static void checkNotNegative(String name, long value) {
        checkArgument(value >= 0, "%s must not be negative: %s", name, value);
    }
}
