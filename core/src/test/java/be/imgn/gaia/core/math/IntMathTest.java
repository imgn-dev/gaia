package be.imgn.gaia.core.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class IntMathTest {

    // Values next to the limits, to powers of two and ten, and to squares, plus random ones.
    private static final int[] VALUES = values();

    private static int[] values() {
        var values = new TreeSet<Integer>();
        for (long base = 1; base <= 1L << 31; base *= 2) {
            add(values, base);
        }
        for (long base = 1; base <= 1L << 31; base *= 10) {
            add(values, base);
        }
        for (long root = 1; root < 1L << 16; root = root * 3 + 1) {
            add(values, root * root);
        }
        add(values, 46_340L * 46_340L);
        add(values, 46_341L * 46_341L);
        var random = new SplittableRandom(1);
        for (int i = 0; i < 100; i++) {
            values.add(random.nextInt());
            values.add(random.nextInt(-1_000, 1_000));
        }
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    // n - 1, n and n + 1, and their negations, that fit in an int.
    private static void add(Set<Integer> values, long n) {
        for (long value = n - 1; value <= n + 1; value++) {
            for (long signed : new long[] {value, -value}) {
                if (signed == (int) signed) {
                    values.add((int) signed);
                }
            }
        }
    }

    private static IntStream positiveValues() {
        return Arrays.stream(VALUES).filter(x -> x > 0);
    }

    private static BigInteger big(long x) {
        return BigInteger.valueOf(x);
    }

    // Asserts that a call returns an exact result, or throws ArithmeticException if it doesn't fit in an int.
    private static void assertExact(IntSupplier call, BigInteger exact, Supplier<String> description) {
        if (exact.bitLength() < Integer.SIZE) {
            assertThat(call.getAsInt()).as(description).isEqualTo(exact.intValueExact());
        } else {
            assertThatExceptionOfType(ArithmeticException.class).as(description).isThrownBy(call::getAsInt);
        }
    }

    private static int saturated(BigInteger exact) {
        return exact.max(big(Integer.MIN_VALUE)).min(big(Integer.MAX_VALUE)).intValueExact();
    }

    // Asserts how a call rounds a result that is never halfway between two integers.
    private static void assertRounded(
            IntSupplier call,
            RoundingMode mode,
            long floor,
            boolean exact,
            boolean aboveHalf,
            Supplier<String> description) {
        if (mode == RoundingMode.UNNECESSARY && !exact) {
            assertThatExceptionOfType(ArithmeticException.class)
                    .as(description)
                    .isThrownBy(call::getAsInt)
                    .withMessage("Rounding necessary");
            return;
        }
        var up = switch (mode) {
            case UP, CEILING -> !exact;
            case HALF_UP, HALF_DOWN, HALF_EVEN -> aboveHalf;
            case DOWN, FLOOR, UNNECESSARY -> false;
        };
        assertThat(call.getAsInt()).as(description).isEqualTo(floor + (up ? 1 : 0));
    }

    @Test
    void computesTheGreatestCommonDivisor() {
        for (var a : VALUES) {
            for (var b : VALUES) {
                assertExact(() -> IntMath.gcd(a, b), big(a).gcd(big(b)), () -> "gcd(%d, %d)".formatted(a, b));
            }
        }
        assertThat(IntMath.gcd(0, 0)).isZero();
        assertThat(IntMath.gcd(Integer.MIN_VALUE, Integer.MIN_VALUE + 2)).isEqualTo(2);
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> IntMath.gcd(Integer.MIN_VALUE, 0))
                .withMessage("integer overflow");
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> IntMath.gcd(Integer.MIN_VALUE, Integer.MIN_VALUE))
                .withMessage("integer overflow");
    }

    @Test
    void computesTheGreatestCommonDivisorOfRandomValues() {
        var random = new SplittableRandom(2);
        for (int i = 0; i < 100_000; i++) {
            var a = random.nextInt();
            var b = random.nextInt();
            // Shared factors, so that the result isn't always 1.
            var factor = random.nextInt(1, 1_000);
            var c = a / factor * factor;
            var d = b / factor * factor;
            assertExact(() -> IntMath.gcd(a, b), big(a).gcd(big(b)), () -> "gcd(%d, %d)".formatted(a, b));
            assertExact(() -> IntMath.gcd(c, d), big(c).gcd(big(d)), () -> "gcd(%d, %d)".formatted(c, d));
        }
    }

    @Test
    void computesTheLeastCommonMultiple() {
        for (var a : VALUES) {
            for (var b : VALUES) {
                var gcd = big(a).gcd(big(b));
                var lcm = gcd.signum() == 0
                        ? BigInteger.ZERO
                        : big(a).multiply(big(b)).abs().divide(gcd);
                assertExact(() -> IntMath.lcmExact(a, b), lcm, () -> "lcmExact(%d, %d)".formatted(a, b));
            }
        }
        assertThat(IntMath.lcmExact(-4, 6)).isEqualTo(12);
    }

    @Test
    void findsPowersOfTwo() {
        for (var x : VALUES) {
            assertThat(IntMath.isPowerOfTwo(x)).as("%d", x).isEqualTo(x > 0 && big(x).bitCount() == 1);
        }
        for (var x : positiveValues().toArray()) {
            var bits = big(x).bitLength();
            assertThat(IntMath.floorPowerOfTwo(x)).as("%d", x).isEqualTo(1 << (bits - 1));
            var ceiling = big(x - 1).bitLength();
            assertExact(() -> IntMath.ceilingPowerOfTwo(x), BigInteger.ONE.shiftLeft(ceiling), () -> "%d".formatted(x));
        }
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IntMath.floorPowerOfTwo(0))
                .withMessage("x must be positive: 0");
        assertThatIllegalArgumentException().isThrownBy(() -> IntMath.ceilingPowerOfTwo(-1));
    }

    @Test
    void roundsBase2Logarithms() {
        for (var x : positiveValues().toArray()) {
            var floor = big(x).bitLength() - 1;
            var exact = big(x).bitCount() == 1;
            // x > 2^(floor + 1/2), squared.
            var aboveHalf = big(x).pow(2).compareTo(BigInteger.ONE.shiftLeft(2 * floor + 1)) > 0;
            for (var mode : RoundingMode.values()) {
                assertRounded(
                        () -> IntMath.log2(x, mode),
                        mode,
                        floor,
                        exact,
                        aboveHalf,
                        () -> "log2(%d, %s)".formatted(x, mode));
            }
        }
        assertThat(IntMath.log2(181, RoundingMode.HALF_UP)).isEqualTo(7);
        assertThat(IntMath.log2(182, RoundingMode.HALF_DOWN)).isEqualTo(8);
    }

    @Test
    void roundsBase10Logarithms() {
        for (var x : positiveValues().toArray()) {
            var floor = Integer.toString(x).length() - 1;
            var power = BigInteger.TEN.pow(floor);
            var exact = big(x).equals(power);
            // x > 10^(floor + 1/2), squared.
            var aboveHalf = big(x).pow(2).compareTo(power.pow(2).multiply(BigInteger.TEN)) > 0;
            for (var mode : RoundingMode.values()) {
                assertRounded(
                        () -> IntMath.log10(x, mode),
                        mode,
                        floor,
                        exact,
                        aboveHalf,
                        () -> "log10(%d, %s)".formatted(x, mode));
            }
        }
        assertThat(IntMath.log10(316, RoundingMode.HALF_UP)).isEqualTo(2);
        assertThat(IntMath.log10(317, RoundingMode.HALF_DOWN)).isEqualTo(3);
    }

    @Test
    void roundsSquareRoots() {
        var values = IntStream.concat(
                        Arrays.stream(VALUES).filter(x -> x >= 0),
                        IntStream.rangeClosed(1, 46_340).flatMap(r -> IntStream.of(r * r - 1, r * r, r * r + 1)))
                .filter(x -> x >= 0)
                .toArray();
        for (var x : values) {
            var floor = big(x).sqrt();
            var exact = floor.pow(2).equals(big(x));
            // x > (floor + 1/2)^2, times 4.
            var aboveHalf = big(x).shiftLeft(2)
                            .compareTo(floor.shiftLeft(1).add(BigInteger.ONE).pow(2))
                    > 0;
            for (var mode : RoundingMode.values()) {
                assertRounded(
                        () -> IntMath.sqrt(x, mode),
                        mode,
                        floor.longValueExact(),
                        exact,
                        aboveHalf,
                        () -> "sqrt(%d, %s)".formatted(x, mode));
            }
        }
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IntMath.sqrt(-1, RoundingMode.FLOOR))
                .withMessage("x must not be negative: -1");
    }

    @Test
    void roundsQuotients() {
        for (var p : VALUES) {
            for (var q : VALUES) {
                if (q == 0 || (p == Integer.MIN_VALUE && q == -1)) {
                    continue;
                }
                for (var mode : RoundingMode.values()) {
                    Supplier<String> description = () -> "divide(%d, %d, %s)".formatted(p, q, mode);
                    if (mode == RoundingMode.UNNECESSARY && p % q != 0) {
                        assertThatExceptionOfType(ArithmeticException.class)
                                .as(description)
                                .isThrownBy(() -> IntMath.divide(p, q, mode))
                                .withMessage("Rounding necessary");
                    } else {
                        var expected = new BigDecimal(p).divide(new BigDecimal(q), 0, mode);
                        assertThat(IntMath.divide(p, q, mode)).as(description).isEqualTo(expected.intValueExact());
                    }
                }
            }
        }
    }

    @Test
    void roundsQuotientsThatAreNotIntegers() {
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> IntMath.divide(7, 2, RoundingMode.UNNECESSARY))
                .withMessage("Rounding necessary");
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> IntMath.divide(7, 0, RoundingMode.DOWN));
        for (var mode : RoundingMode.values()) {
            assertThat(IntMath.divide(Integer.MIN_VALUE, -1, mode)).isEqualTo(Integer.MIN_VALUE);
        }
    }

    @Test
    void raisesToPowers() {
        var bases = IntStream.concat(IntStream.rangeClosed(-4, 4), Arrays.stream(VALUES))
                .distinct()
                .toArray();
        for (var base : bases) {
            for (int exponent = 0; exponent <= 64; exponent++) {
                var e = exponent;
                var exact = big(base).pow(exponent);
                Supplier<String> description = () -> "%d^%d".formatted(base, e);
                assertThat(IntMath.pow(base, e)).as(description).isEqualTo(exact.intValue());
                assertThat(IntMath.saturatedPow(base, e)).as(description).isEqualTo(saturated(exact));
            }
        }
        assertThat(IntMath.pow(-1, Integer.MAX_VALUE)).isEqualTo(-1);
        assertThat(IntMath.saturatedPow(-3, Integer.MAX_VALUE)).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void rejectsNegativeExponents() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IntMath.pow(2, -1))
                .withMessage("exponent must not be negative: -1");
        assertThatIllegalArgumentException().isThrownBy(() -> IntMath.saturatedPow(2, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> IntMath.powMod(2, -1, 3));
    }

    @Test
    void saturatesArithmetic() {
        for (var a : VALUES) {
            for (var b : VALUES) {
                Supplier<String> description = () -> "(%d, %d)".formatted(a, b);
                assertThat(IntMath.saturatedAdd(a, b)).as(description).isEqualTo(saturated(big(a).add(big(b))));
                assertThat(IntMath.saturatedSubtract(a, b))
                        .as(description)
                        .isEqualTo(saturated(big(a).subtract(big(b))));
                assertThat(IntMath.saturatedMultiply(a, b))
                        .as(description)
                        .isEqualTo(saturated(big(a).multiply(big(b))));
            }
        }
    }

    @Test
    void raisesToPowersModuloAModulus() {
        var exponents = new int[] {0, 1, 2, 3, 10, 31, 32, 63, 1_000_003, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
        var moduli = new int[] {
            1, 2, 3, 4, 7, 10, 1_000, 46_341, 65_521, 65_536, 1 << 30, Integer.MAX_VALUE - 1, Integer.MAX_VALUE
        };
        for (var base : VALUES) {
            for (var exponent : exponents) {
                for (var modulus : moduli) {
                    var expected = big(base).modPow(big(exponent), big(modulus));
                    assertThat(IntMath.powMod(base, exponent, modulus))
                            .as("%d^%d mod %d", base, exponent, modulus)
                            .isEqualTo(expected.intValueExact());
                }
            }
        }
        var random = new SplittableRandom(4);
        for (int i = 0; i < 100_000; i++) {
            var base = random.nextInt();
            var exponent = random.nextInt(0, Integer.MAX_VALUE);
            var modulus = random.nextInt(1, Integer.MAX_VALUE);
            assertThat(IntMath.powMod(base, exponent, modulus))
                    .as("%d^%d mod %d", base, exponent, modulus)
                    .isEqualTo(big(base).modPow(big(exponent), big(modulus)).intValueExact());
        }
        assertThat(IntMath.powMod(3, 200, 1_000_000_007)).isEqualTo(136_318_165);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IntMath.powMod(2, 3, 0))
                .withMessage("modulus must be positive: 0");
    }

    @Test
    void computesMeans() {
        for (var a : VALUES) {
            for (var b : VALUES) {
                assertThat(IntMath.mean(a, b)).as("mean(%d, %d)", a, b).isEqualTo(Math.floorDiv((long) a + b, 2));
            }
        }
    }

    @Test
    void computesFactorials() {
        var factorial = BigInteger.ONE;
        for (int n = 0; n <= 40; n++) {
            if (n > 0) {
                factorial = factorial.multiply(big(n));
            }
            var m = n;
            assertExact(() -> IntMath.factorial(m), factorial, () -> "%d!".formatted(m));
        }
        assertThat(IntMath.factorial(12)).isEqualTo(479_001_600);
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> IntMath.factorial(Integer.MAX_VALUE));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IntMath.factorial(-1))
                .withMessage("n must not be negative: -1");
    }

    @Test
    void computesBinomialCoefficients() {
        for (int n = 0; n <= 70; n++) {
            var coefficient = BigInteger.ONE;
            for (int k = 0; k <= n; k++) {
                if (k > 0) {
                    coefficient = coefficient.multiply(big(n - k + 1)).divide(big(k));
                }
                var m = n;
                var j = k;
                assertExact(() -> IntMath.binomial(m, j), coefficient, () -> "binomial(%d, %d)".formatted(m, j));
            }
        }
        assertThat(IntMath.binomial(Integer.MAX_VALUE, 1)).isEqualTo(Integer.MAX_VALUE);
        assertThat(IntMath.binomial(Integer.MAX_VALUE, Integer.MAX_VALUE - 1)).isEqualTo(Integer.MAX_VALUE);
        assertThat(IntMath.binomial(65_536, 2)).isEqualTo(2_147_450_880);
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> IntMath.binomial(Integer.MAX_VALUE, Integer.MAX_VALUE / 2))
                .withMessage("integer overflow");
    }

    @Test
    void rejectsInvalidBinomialCoefficients() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IntMath.binomial(3, -1))
                .withMessage("k must not be negative: -1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> IntMath.binomial(3, 4))
                .withMessage("k must not exceed n: 4 > 3");
        assertThatIllegalArgumentException().isThrownBy(() -> IntMath.binomial(-1, 0));
    }

    @Test
    void findsPrimesBelowAMillion() {
        var limit = 1_000_000;
        var composite = new boolean[limit];
        for (int i = 2; i * i < limit; i++) {
            if (!composite[i]) {
                for (int j = i * i; j < limit; j += i) {
                    composite[j] = true;
                }
            }
        }
        assertThat(IntStream.range(-10, limit).filter(n -> IntMath.isPrime(n) != (n >= 2 && !composite[n])))
                .isEmpty();
    }

    @Test
    void findsLargePrimes() {
        var random = new SplittableRandom(3);
        var values = IntStream.concat(
                IntStream.rangeClosed(Integer.MAX_VALUE - 100_000, Integer.MAX_VALUE),
                random.ints(100_000, 1, Integer.MAX_VALUE));
        assertThat(values.filter(n -> IntMath.isPrime(n) != big(n).isProbablePrime(100)))
                .isEmpty();
        assertThat(IntMath.isPrime(Integer.MIN_VALUE)).isFalse();
    }

    @Test
    void findsCompositesThatPassSomeTests() {
        // Strong pseudoprimes to base 2, to bases 2 and 3, and to bases 2, 3 and 5; then Carmichael numbers.
        var composites = new int[] {2_047, 3_277, 1_373_653, 25_326_001, 561, 1_105, 1_729, 2_465, 2_821, 6_601};
        for (var n : composites) {
            assertThat(IntMath.isPrime(n)).as("%d", n).isFalse();
        }
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsANullRoundingMode() {
        assertThatNullPointerException().isThrownBy(() -> IntMath.log2(1, null)).withMessage("mode");
        assertThatNullPointerException()
                .isThrownBy(() -> IntMath.log10(1, null))
                .withMessage("mode");
        assertThatNullPointerException().isThrownBy(() -> IntMath.sqrt(1, null)).withMessage("mode");
        assertThatNullPointerException()
                .isThrownBy(() -> IntMath.divide(1, 1, null))
                .withMessage("mode");
    }
}
