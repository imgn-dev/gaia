package be.imgn.gaia.core.math;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.math.RoundingMode;
import java.util.Arrays;

/**
 * Arithmetic on {@code int} values that {@link Math} doesn't provide: divisors, powers, logarithms, square roots and
 * divisions with a rounding mode, saturated arithmetic, combinatorics and primality.
 *
 * <pre>{@code
 * IntMath.gcd(-12, 18)                        // 6
 * IntMath.log2(1000, RoundingMode.CEILING)    // 10
 * IntMath.sqrt(99, RoundingMode.HALF_EVEN)    // 10
 * IntMath.divide(7, 2, RoundingMode.HALF_UP)  // 4
 * IntMath.saturatedAdd(Integer.MAX_VALUE, 1)  // Integer.MAX_VALUE
 * IntMath.powMod(3, 200, 1_000_000_007)       // 136318165
 * }</pre>
 *
 * <p>A method whose result may not fit in an {@code int} throws {@link ArithmeticException} when it doesn't, except
 * {@link #pow(int, int)}, which wraps around like {@code int} multiplication, the {@code saturated} methods, which
 * return {@link Integer#MAX_VALUE} or {@link Integer#MIN_VALUE} instead, and {@link #divide(int, int, RoundingMode)},
 * which overflows like {@link Math#floorDiv(int, int)}.
 *
 * @since 0.1.3
 * @see LongMath
 */
public final class IntMath {

    // floor(2^31.5): the integer part of the midpoint between 2^31 and 2^32, in logarithmic scale.
    private static final int FLOOR_SQRT_2_POW_31 = 0xB504_F333;

    // log10(2) is about LOG10_2_NUMERATOR / 2^LOG10_2_SHIFT.
    private static final int LOG10_2_NUMERATOR = 1233;
    private static final int LOG10_2_SHIFT = 12;

    private static final int[] POWERS_OF_10 = {
        1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, 1_000_000_000
    };

    // floor(10^(k + 1/2)) at index k, except the last one, which doesn't fit and no int exceeds.
    private static final int[] HALF_POWERS_OF_10 = {
        3, 31, 316, 3_162, 31_622, 316_227, 3_162_277, 31_622_776, 316_227_766, Integer.MAX_VALUE
    };

    private static final int[] FACTORIALS = {
        1, 1, 2, 6, 24, 120, 720, 5_040, 40_320, 362_880, 3_628_800, 39_916_800, 479_001_600
    };

    // Odd primes that a value is checked against first, since most composite numbers have one as a factor.
    private static final int[] SMALL_PRIMES = {3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53};

    // x is a multiple of p if and only if x * p^-1 mod 2^32 is at most (2^32 - 1) / p, unsigned: multiplying by the
    // inverse undoes the multiplications by p, and sends every other value above them. The low half of the inverse
    // modulo 2^64 is the inverse modulo 2^32.
    private static final int[] SMALL_PRIME_INVERSES =
            Arrays.stream(SMALL_PRIMES).map(p -> (int) Montgomery.inverse(p)).toArray();
    private static final int[] SMALL_PRIME_QUOTIENTS =
            Arrays.stream(SMALL_PRIMES).map(p -> Integer.divideUnsigned(-1, p)).toArray();

    // An odd value that no small prime divides is prime if it's less than the square of the largest one.
    private static final int SMALL_PRIMES_SQUARE = 53 * 53;

    // The Miller-Rabin test with these bases is never wrong below 4,759,123,141.
    private static final int[] PRIME_BASES = {2, 7, 61};

    private IntMath() {}

    /**
     * Returns the greatest common divisor of two values: the largest positive integer that divides both, or 0 if both
     * are 0. Signs don't matter: {@code gcd(-12, 18)} is 6.
     *
     * @param a a value
     * @param b another value
     * @return the greatest common divisor of {@code a} and {@code b}, never negative
     * @throws ArithmeticException if the result, {@code 2^31}, doesn't fit in an {@code int}: when {@code a} and
     *     {@code b} are each {@link Integer#MIN_VALUE} or 0, but not both 0
     */
    public static int gcd(int a, int b) {
        if (a == 0 || b == 0) {
            return checkedAbs(a | b);
        }
        // Binary GCD, which only subtracts and shifts: a division takes longer. Both values stay odd, and the shift of
        // their difference is counted while their minimum is taken, so that the steps don't wait on each other.
        // Unsigned shifts take Integer.MIN_VALUE, its own absolute value, as 2^31.
        var aTwos = Integer.numberOfTrailingZeros(a);
        var bTwos = Integer.numberOfTrailingZeros(b);
        var u = Math.abs(a) >>> aTwos;
        var v = Math.abs(b);
        var vTwos = bTwos;
        while (true) {
            v >>>= vTwos;
            var difference = v - u;
            if (difference == 0) {
                return checkedAbs(u << Math.min(aTwos, bTwos));
            }
            vTwos = Integer.numberOfTrailingZeros(difference);
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
     * @throws ArithmeticException if the result doesn't fit in an {@code int}
     */
    public static int lcmExact(int a, int b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return Math.toIntExact(Math.abs((long) (a / gcd(a, b)) * b));
    }

    /**
     * Returns whether a value is a power of two: 1, 2, 4, 8 and so on.
     *
     * @param x the value
     * @return whether {@code x} is a power of two, false for 0 and negative values
     */
    public static boolean isPowerOfTwo(int x) {
        return x > 0 && (x & (x - 1)) == 0;
    }

    /**
     * Returns the largest power of two that doesn't exceed a value.
     *
     * @param x the value
     * @return the largest power of two not above {@code x}
     * @throws IllegalArgumentException if {@code x} isn't positive
     */
    public static int floorPowerOfTwo(int x) {
        checkPositive("x", x);
        return Integer.highestOneBit(x);
    }

    /**
     * Returns the smallest power of two that isn't less than a value.
     *
     * @param x the value
     * @return the smallest power of two not below {@code x}
     * @throws IllegalArgumentException if {@code x} isn't positive
     * @throws ArithmeticException if the result doesn't fit in an {@code int}: when {@code x} exceeds {@code 2^30}
     */
    public static int ceilingPowerOfTwo(int x) {
        checkPositive("x", x);
        if (x > 1 << (Integer.SIZE - 2)) {
            throw new ArithmeticException("integer overflow");
        }
        return 1 << -Integer.numberOfLeadingZeros(x - 1);
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
    public static int log2(int x, RoundingMode mode) {
        checkPositive("x", x);
        requireNonNull(mode, "mode");
        var leadingZeros = Integer.numberOfLeadingZeros(x);
        var floor = Integer.SIZE - 1 - leadingZeros;
        if (isPowerOfTwo(x)) {
            return floor;
        }
        // The midpoint, 2^(floor + 1/2), is irrational: x is above it if it is above its integer part.
        var aboveHalf = x > FLOOR_SQRT_2_POW_31 >>> leadingZeros;
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
    public static int log10(int x, RoundingMode mode) {
        checkPositive("x", x);
        requireNonNull(mode, "mode");
        // The result or one more, for every bit length up to 31.
        var guess = ((Integer.SIZE - Integer.numberOfLeadingZeros(x)) * LOG10_2_NUMERATOR) >>> LOG10_2_SHIFT;
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
    public static int sqrt(int x, RoundingMode mode) {
        checkArgument(x >= 0, "x must not be negative: %s", x);
        requireNonNull(mode, "mode");
        // Exact: a double holds every int, and Math.sqrt rounds correctly, never up to the next integer.
        var floor = (int) Math.sqrt(x);
        var remainder = x - floor * floor;
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
     * {@link Math#floorDiv(int, int)}. Like them, it returns {@link Integer#MIN_VALUE} for {@code Integer.MIN_VALUE /
     * -1}, which overflows.
     *
     * @param p the dividend
     * @param q the divisor
     * @param mode how to round the quotient
     * @return the quotient of {@code p} by {@code q}, rounded with {@code mode}
     * @throws ArithmeticException if {@code q} is 0, or if {@code mode} is {@link RoundingMode#UNNECESSARY} and
     *     {@code q} doesn't divide {@code p}
     */
    public static int divide(int p, int q, RoundingMode mode) {
        requireNonNull(mode, "mode");
        var quotient = p / q;
        var remainder = p - q * quotient;
        if (remainder == 0) {
            return quotient;
        }
        var negative = (p ^ q) < 0;
        // |remainder| against |q| - |remainder|, unsigned so that |Integer.MIN_VALUE| counts as 2^31.
        var absRemainder = Math.abs(remainder);
        var halfComparison = Integer.compareUnsigned(absRemainder, Math.abs(q) - absRemainder);
        if (Rounding.awayFromZero(mode, negative, halfComparison, (quotient & 1) != 0)) {
            return negative ? quotient - 1 : quotient + 1;
        }
        return quotient;
    }

    /**
     * Returns a value to a power, wrapping around on overflow like {@code int} multiplication.
     *
     * @param base the base
     * @param exponent the exponent
     * @return {@code base} to the power of {@code exponent}, keeping only its 32 lowest bits
     * @throws IllegalArgumentException if {@code exponent} is negative
     */
    public static int pow(int base, int exponent) {
        checkNotNegative("exponent", exponent);
        var result = 1;
        while (exponent != 0) {
            if ((exponent & 1) != 0) {
                result *= base;
            }
            base *= base;
            exponent >>>= 1;
        }
        return result;
    }

    /**
     * Returns a value to a power.
     *
     * @param base the base
     * @param exponent the exponent
     * @return {@code base} to the power of {@code exponent}
     * @throws IllegalArgumentException if {@code exponent} is negative
     * @throws ArithmeticException if the result doesn't fit in an {@code int}
     */
    public static int powExact(int base, int exponent) {
        checkNotNegative("exponent", exponent);
        var result = 1;
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
     * Returns the sum of two values, or the {@code int} closest to it if it doesn't fit.
     *
     * @param a a value
     * @param b another value
     * @return {@code a + b}, clamped between {@link Integer#MIN_VALUE} and {@link Integer#MAX_VALUE}
     */
    public static int saturatedAdd(int a, int b) {
        return Math.clamp((long) a + b, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Returns the difference of two values, or the {@code int} closest to it if it doesn't fit.
     *
     * @param a a value
     * @param b the value to subtract from {@code a}
     * @return {@code a - b}, clamped between {@link Integer#MIN_VALUE} and {@link Integer#MAX_VALUE}
     */
    public static int saturatedSubtract(int a, int b) {
        return Math.clamp((long) a - b, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Returns the product of two values, or the {@code int} closest to it if it doesn't fit.
     *
     * @param a a value
     * @param b another value
     * @return {@code a * b}, clamped between {@link Integer#MIN_VALUE} and {@link Integer#MAX_VALUE}
     */
    public static int saturatedMultiply(int a, int b) {
        return Math.clamp((long) a * b, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Returns a value to a power, or the {@code int} closest to it if it doesn't fit.
     *
     * @param base the base
     * @param exponent the exponent
     * @return {@code base} to the power of {@code exponent}, clamped between {@link Integer#MIN_VALUE} and
     *     {@link Integer#MAX_VALUE}
     * @throws IllegalArgumentException if {@code exponent} is negative
     */
    public static int saturatedPow(int base, int exponent) {
        checkNotNegative("exponent", exponent);
        var limit = base < 0 && (exponent & 1) != 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        // In longs, where the product of two ints always fits.
        long result = 1;
        long square = base;
        while (true) {
            if ((exponent & 1) != 0) {
                result *= square;
                if (result != (int) result) {
                    return limit;
                }
            }
            exponent >>>= 1;
            if (exponent == 0) {
                return (int) result;
            }
            square *= square;
            // A factor of the result, which overflows too if this does.
            if (square > Integer.MAX_VALUE) {
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
    public static int powMod(int base, int exponent, int modulus) {
        checkNotNegative("exponent", exponent);
        checkPositive("modulus", modulus);
        return (int) new Barrett(modulus).pow(Math.floorMod(base, modulus), exponent);
    }

    /**
     * Returns the mean of two values, rounded toward negative infinity. Unlike {@code (a + b) / 2}, it never overflows.
     *
     * @param a a value
     * @param b another value
     * @return the mean of {@code a} and {@code b}, rounded toward negative infinity
     */
    public static int mean(int a, int b) {
        // The bits both have, plus half the bits only one has.
        return (a & b) + ((a ^ b) >> 1);
    }

    /**
     * Returns the factorial of a value: the product of the integers from 1 to it.
     *
     * @param n the value
     * @return {@code n!}, which is 1 for 0
     * @throws IllegalArgumentException if {@code n} is negative
     * @throws ArithmeticException if the result doesn't fit in an {@code int}: when {@code n} exceeds 12
     */
    public static int factorial(int n) {
        checkNotNegative("n", n);
        if (n >= FACTORIALS.length) {
            throw new ArithmeticException("integer overflow");
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
     * @throws ArithmeticException if the result doesn't fit in an {@code int}
     */
    public static int binomial(int n, int k) {
        checkNotNegative("k", k);
        checkArgument(k <= n, "k must not exceed n: %s > %s", k, n);
        k = Math.min(k, n - k);
        long result = 1;
        for (int i = 1; i <= k; i++) {
            // binomial(n - k + i, i), which grows with i up to the result: at least twice as large on each step, so
            // the loop ends soon when the result overflows.
            result = result * (n - k + i) / i;
            if (result > Integer.MAX_VALUE) {
                throw new ArithmeticException("integer overflow");
            }
        }
        return (int) result;
    }

    /**
     * Returns whether a value is a prime number: an integer greater than 1 that only 1 and itself divide.
     *
     * <p>The answer is always exact.
     *
     * @param n the value
     * @return whether {@code n} is prime, false for values less than 2
     */
    public static boolean isPrime(int n) {
        if (n < 2 || (n & 1) == 0) {
            return n == 2;
        }
        var factor = smallPrimeFactor(n);
        if (factor != 0) {
            return n == factor;
        }
        return n < SMALL_PRIMES_SQUARE || passesMillerRabin(n);
    }

    // Returns the smallest of the small primes that divides an odd n, or 0 if none does.
    private static int smallPrimeFactor(int n) {
        for (int i = 0; i < SMALL_PRIMES.length; i++) {
            if (Integer.compareUnsigned(n * SMALL_PRIME_INVERSES[i], SMALL_PRIME_QUOTIENTS[i]) <= 0) {
                return SMALL_PRIMES[i];
            }
        }
        return 0;
    }

    // For an odd n greater than every base.
    private static boolean passesMillerRabin(int n) {
        var barrett = new Barrett(n);
        for (var base : PRIME_BASES) {
            if (!isStrongProbablePrime(barrett, n, base)) {
                return false;
            }
        }
        return true;
    }

    // For an odd n greater than the base.
    private static boolean isStrongProbablePrime(Barrett barrett, int n, int base) {
        var d = n - 1;
        var s = Integer.numberOfTrailingZeros(d);
        var x = barrett.pow(base, d >>> s);
        if (x == 1 || x == n - 1) {
            return true;
        }
        for (int i = 1; i < s; i++) {
            x = barrett.multiply(x, x);
            if (x == n - 1) {
                return true;
            }
        }
        return false;
    }

    private static int checkedAbs(int x) {
        if (x == Integer.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        }
        return Math.abs(x);
    }

    private static void checkPositive(String name, int value) {
        checkArgument(value > 0, "%s must be positive: %s", name, value);
    }

    private static void checkNotNegative(String name, int value) {
        checkArgument(value >= 0, "%s must not be negative: %s", name, value);
    }
}
