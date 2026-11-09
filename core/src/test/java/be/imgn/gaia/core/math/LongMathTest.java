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
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LongMathTest {

    private static final BigInteger MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);

    // Values next to the limits, to powers of two and ten, and to squares, plus random ones.
    private static final long[] VALUES = values();

    private static long[] values() {
        var values = new TreeSet<Long>();
        for (int bits = 0; bits <= Long.SIZE; bits++) {
            add(values, BigInteger.ONE.shiftLeft(bits));
        }
        for (int digits = 0; digits <= 19; digits++) {
            add(values, BigInteger.TEN.pow(digits));
        }
        for (long root = 1; root <= 3_037_000_500L; root = root * 3 + 1) {
            add(values, big(root).pow(2));
        }
        add(values, big(3_037_000_499L).pow(2));
        add(values, big(3_037_000_500L).pow(2));
        var random = new SplittableRandom(1);
        for (int i = 0; i < 100; i++) {
            values.add(random.nextLong());
            values.add(random.nextLong(-1_000, 1_000));
            values.add((long) random.nextInt());
        }
        return values.stream().mapToLong(Long::longValue).toArray();
    }

    // n - 1, n and n + 1, and their negations, that fit in a long.
    private static void add(Set<Long> values, BigInteger n) {
        for (var value : new BigInteger[] {n.subtract(BigInteger.ONE), n, n.add(BigInteger.ONE)}) {
            for (var signed : new BigInteger[] {value, value.negate()}) {
                if (signed.bitLength() < Long.SIZE) {
                    values.add(signed.longValueExact());
                }
            }
        }
    }

    private static LongStream positiveValues() {
        return Arrays.stream(VALUES).filter(x -> x > 0);
    }

    private static BigInteger big(long x) {
        return BigInteger.valueOf(x);
    }

    // Asserts that a call returns an exact result, or throws ArithmeticException if it doesn't fit in a long.
    private static void assertExact(LongSupplier call, BigInteger exact, Supplier<String> description) {
        if (exact.bitLength() < Long.SIZE) {
            assertThat(call.getAsLong()).as(description).isEqualTo(exact.longValueExact());
        } else {
            assertThatExceptionOfType(ArithmeticException.class).as(description).isThrownBy(call::getAsLong);
        }
    }

    private static long saturated(BigInteger exact) {
        return exact.max(MIN).min(MAX).longValueExact();
    }

    // Asserts how a call rounds a result that is never halfway between two integers.
    private static void assertRounded(
            LongSupplier call,
            RoundingMode mode,
            long floor,
            boolean exact,
            boolean aboveHalf,
            Supplier<String> description) {
        if (mode == RoundingMode.UNNECESSARY && !exact) {
            assertThatExceptionOfType(ArithmeticException.class)
                    .as(description)
                    .isThrownBy(call::getAsLong)
                    .withMessage("Rounding necessary");
            return;
        }
        var up = switch (mode) {
            case UP, CEILING -> !exact;
            case HALF_UP, HALF_DOWN, HALF_EVEN -> aboveHalf;
            case DOWN, FLOOR, UNNECESSARY -> false;
        };
        assertThat(call.getAsLong()).as(description).isEqualTo(floor + (up ? 1 : 0));
    }

    @Test
    void computesTheGreatestCommonDivisor() {
        for (var a : VALUES) {
            for (var b : VALUES) {
                assertExact(() -> LongMath.gcd(a, b), big(a).gcd(big(b)), () -> "gcd(%d, %d)".formatted(a, b));
            }
        }
        assertThat(LongMath.gcd(0, 0)).isZero();
        assertThat(LongMath.gcd(Long.MIN_VALUE, Long.MIN_VALUE + 2)).isEqualTo(2);
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> LongMath.gcd(0, Long.MIN_VALUE))
                .withMessage("long overflow");
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> LongMath.gcd(Long.MIN_VALUE, Long.MIN_VALUE))
                .withMessage("long overflow");
    }

    @Test
    void computesTheGreatestCommonDivisorOfRandomValues() {
        var random = new SplittableRandom(2);
        for (int i = 0; i < 100_000; i++) {
            var a = random.nextLong();
            var b = random.nextLong();
            // Shared factors, so that the result isn't always 1.
            var factor = random.nextLong(1, 1L << 32);
            var c = a / factor * factor;
            var d = b / factor * factor;
            assertExact(() -> LongMath.gcd(a, b), big(a).gcd(big(b)), () -> "gcd(%d, %d)".formatted(a, b));
            assertExact(() -> LongMath.gcd(c, d), big(c).gcd(big(d)), () -> "gcd(%d, %d)".formatted(c, d));
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
                assertExact(() -> LongMath.lcmExact(a, b), lcm, () -> "lcmExact(%d, %d)".formatted(a, b));
            }
        }
        assertThat(LongMath.lcmExact(-4, 6)).isEqualTo(12);
    }

    @Test
    void findsPowersOfTwo() {
        for (var x : VALUES) {
            assertThat(LongMath.isPowerOfTwo(x)).as("%d", x).isEqualTo(x > 0 && big(x).bitCount() == 1);
        }
        for (var x : positiveValues().toArray()) {
            var bits = big(x).bitLength();
            assertThat(LongMath.floorPowerOfTwo(x)).as("%d", x).isEqualTo(1L << (bits - 1));
            var ceiling = big(x - 1).bitLength();
            assertExact(
                    () -> LongMath.ceilingPowerOfTwo(x), BigInteger.ONE.shiftLeft(ceiling), () -> "%d".formatted(x));
        }
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LongMath.floorPowerOfTwo(0))
                .withMessage("x must be positive: 0");
        assertThatIllegalArgumentException().isThrownBy(() -> LongMath.ceilingPowerOfTwo(-1));
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
                        () -> LongMath.log2(x, mode),
                        mode,
                        floor,
                        exact,
                        aboveHalf,
                        () -> "log2(%d, %s)".formatted(x, mode));
            }
        }
        assertThat(LongMath.log2(0xB504_F333_F9DE_6484L >>> 1, RoundingMode.HALF_UP))
                .isEqualTo(62);
        assertThat(LongMath.log2((0xB504_F333_F9DE_6484L >>> 1) + 1, RoundingMode.HALF_DOWN))
                .isEqualTo(63);
    }

    @Test
    void roundsBase10Logarithms() {
        for (var x : positiveValues().toArray()) {
            var floor = Long.toString(x).length() - 1;
            var power = BigInteger.TEN.pow(floor);
            var exact = big(x).equals(power);
            // x > 10^(floor + 1/2), squared.
            var aboveHalf = big(x).pow(2).compareTo(power.pow(2).multiply(BigInteger.TEN)) > 0;
            for (var mode : RoundingMode.values()) {
                assertRounded(
                        () -> LongMath.log10(x, mode),
                        mode,
                        floor,
                        exact,
                        aboveHalf,
                        () -> "log10(%d, %s)".formatted(x, mode));
            }
        }
        assertThat(LongMath.log10(3_162_277_660_168_379_331L, RoundingMode.HALF_UP))
                .isEqualTo(18);
        assertThat(LongMath.log10(3_162_277_660_168_379_332L, RoundingMode.HALF_DOWN))
                .isEqualTo(19);
    }

    @Test
    void roundsSquareRoots() {
        var random = new SplittableRandom(3);
        var roots = LongStream.concat(
                LongStream.concat(
                        LongStream.rangeClosed(1, 100_000),
                        LongStream.rangeClosed(3_037_000_499L - 100_000, 3_037_000_499L)),
                random.longs(100_000, 1, 3_037_000_500L));
        var values = LongStream.concat(
                        Arrays.stream(VALUES), roots.flatMap(r -> LongStream.of(r * r - 1, r * r, r * r + 1)))
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
                        () -> LongMath.sqrt(x, mode),
                        mode,
                        floor.longValueExact(),
                        exact,
                        aboveHalf,
                        () -> "sqrt(%d, %s)".formatted(x, mode));
            }
        }
        assertThat(LongMath.sqrt(Long.MAX_VALUE, RoundingMode.FLOOR)).isEqualTo(3_037_000_499L);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LongMath.sqrt(-1, RoundingMode.FLOOR))
                .withMessage("x must not be negative: -1");
    }

    @Test
    void roundsQuotients() {
        for (var p : VALUES) {
            for (var q : VALUES) {
                if (q == 0 || (p == Long.MIN_VALUE && q == -1)) {
                    continue;
                }
                for (var mode : RoundingMode.values()) {
                    Supplier<String> description = () -> "divide(%d, %d, %s)".formatted(p, q, mode);
                    if (mode == RoundingMode.UNNECESSARY && p % q != 0) {
                        assertThatExceptionOfType(ArithmeticException.class)
                                .as(description)
                                .isThrownBy(() -> LongMath.divide(p, q, mode))
                                .withMessage("Rounding necessary");
                    } else {
                        var expected = new BigDecimal(p).divide(new BigDecimal(q), 0, mode);
                        assertThat(LongMath.divide(p, q, mode)).as(description).isEqualTo(expected.longValueExact());
                    }
                }
            }
        }
    }

    @Test
    void roundsQuotientsThatAreNotIntegers() {
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> LongMath.divide(7, 2, RoundingMode.UNNECESSARY))
                .withMessage("Rounding necessary");
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> LongMath.divide(7, 0, RoundingMode.DOWN));
        for (var mode : RoundingMode.values()) {
            assertThat(LongMath.divide(Long.MIN_VALUE, -1, mode)).isEqualTo(Long.MIN_VALUE);
        }
    }

    @Test
    void raisesToPowers() {
        var bases = LongStream.concat(LongStream.rangeClosed(-4, 4), Arrays.stream(VALUES))
                .distinct()
                .toArray();
        for (var base : bases) {
            for (int exponent = 0; exponent <= 128; exponent++) {
                var e = exponent;
                var exact = big(base).pow(exponent);
                Supplier<String> description = () -> "%d^%d".formatted(base, e);
                assertThat(LongMath.pow(base, e)).as(description).isEqualTo(exact.longValue());
                assertThat(LongMath.saturatedPow(base, e)).as(description).isEqualTo(saturated(exact));
            }
        }
        assertThat(LongMath.pow(-1, Integer.MAX_VALUE)).isEqualTo(-1);
        assertThat(LongMath.saturatedPow(-3, Integer.MAX_VALUE)).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void rejectsNegativeExponents() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LongMath.pow(2, -1))
                .withMessage("exponent must not be negative: -1");
        assertThatIllegalArgumentException().isThrownBy(() -> LongMath.saturatedPow(2, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> LongMath.powMod(2, -1, 3));
    }

    @Test
    void saturatesArithmetic() {
        for (var a : VALUES) {
            for (var b : VALUES) {
                Supplier<String> description = () -> "(%d, %d)".formatted(a, b);
                assertThat(LongMath.saturatedAdd(a, b)).as(description).isEqualTo(saturated(big(a).add(big(b))));
                assertThat(LongMath.saturatedSubtract(a, b))
                        .as(description)
                        .isEqualTo(saturated(big(a).subtract(big(b))));
                assertThat(LongMath.saturatedMultiply(a, b))
                        .as(description)
                        .isEqualTo(saturated(big(a).multiply(big(b))));
            }
        }
    }

    @Test
    void raisesToPowersModuloAModulus() {
        var exponents = new long[] {0, 1, 2, 3, 10, 63, 64, 127, 1_000_000_000_000_000_000L, Long.MAX_VALUE};
        var moduli = new long[] {
            1,
            2,
            3,
            4,
            7,
            10,
            (1L << 32) - 1,
            1L << 32,
            (1L << 32) + 15,
            3_037_000_499L,
            3L << 60,
            1L << 62,
            1_000_000_000_000_000_009L,
            Long.MAX_VALUE - 24,
            Long.MAX_VALUE - 1,
            Long.MAX_VALUE
        };
        for (var base : VALUES) {
            for (var exponent : exponents) {
                for (var modulus : moduli) {
                    var expected = big(base).modPow(big(exponent), big(modulus));
                    assertThat(LongMath.powMod(base, exponent, modulus))
                            .as("%d^%d mod %d", base, exponent, modulus)
                            .isEqualTo(expected.longValueExact());
                }
            }
        }
        var random = new SplittableRandom(4);
        for (int i = 0; i < 100_000; i++) {
            var base = random.nextLong();
            var exponent = random.nextLong(0, Long.MAX_VALUE);
            // Any number of factors 2, times an odd value of any size.
            var twos = random.nextInt(Long.SIZE - 1);
            var odd = random.nextLong(1, Long.MAX_VALUE) >>> (twos + random.nextInt(Long.SIZE - 1 - twos)) | 1;
            var modulus = odd << twos;
            assertThat(LongMath.powMod(base, exponent, modulus))
                    .as("%d^%d mod %d", base, exponent, modulus)
                    .isEqualTo(big(base).modPow(big(exponent), big(modulus)).longValueExact());
        }
        assertThat(LongMath.powMod(7, 1_000_000_000_000_000_000L, 1_000_000_000_000_000_009L))
                .isEqualTo(768_868_517_751_089_767L);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LongMath.powMod(2, 3, 0))
                .withMessage("modulus must be positive: 0");
    }

    @Test
    void computesMeans() {
        for (var a : VALUES) {
            for (var b : VALUES) {
                var sum = big(a).add(big(b));
                // BigInteger.divide rounds toward zero, and shiftRight toward negative infinity.
                assertThat(LongMath.mean(a, b))
                        .as("mean(%d, %d)", a, b)
                        .isEqualTo(sum.shiftRight(1).longValueExact());
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
            assertExact(() -> LongMath.factorial(m), factorial, () -> "%d!".formatted(m));
        }
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> LongMath.factorial(Integer.MAX_VALUE))
                .withMessage("long overflow");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LongMath.factorial(-1))
                .withMessage("n must not be negative: -1");
    }

    @Test
    void computesBinomialCoefficients() {
        for (int n = 0; n <= 130; n++) {
            var coefficient = BigInteger.ONE;
            for (int k = 0; k <= n; k++) {
                if (k > 0) {
                    coefficient = coefficient.multiply(big(n - k + 1)).divide(big(k));
                }
                long m = n;
                long j = k;
                assertExact(() -> LongMath.binomial(m, j), coefficient, () -> "binomial(%d, %d)".formatted(m, j));
            }
        }
        assertThat(LongMath.binomial(Long.MAX_VALUE, 1)).isEqualTo(Long.MAX_VALUE);
        assertThat(LongMath.binomial(Long.MAX_VALUE, Long.MAX_VALUE - 1)).isEqualTo(Long.MAX_VALUE);
        assertThat(LongMath.binomial(1L << 32, 2)).isEqualTo(9_223_372_034_707_292_160L);
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> LongMath.binomial(Long.MAX_VALUE, Long.MAX_VALUE / 2));
    }

    @Test
    void rejectsInvalidBinomialCoefficients() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LongMath.binomial(3, -1))
                .withMessage("k must not be negative: -1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LongMath.binomial(3, 4))
                .withMessage("k must not exceed n: 4 > 3");
        assertThatIllegalArgumentException().isThrownBy(() -> LongMath.binomial(-1, 0));
    }

    @Test
    void findsPrimes() {
        var random = new SplittableRandom(5);
        var values = Stream.of(
                        LongStream.rangeClosed(-10, 100_000),
                        LongStream.rangeClosed(Integer.MAX_VALUE - 1_000L, Integer.MAX_VALUE + 1_000L),
                        LongStream.rangeClosed(Long.MAX_VALUE - 10_000, Long.MAX_VALUE),
                        random.longs(100_000, 2, Long.MAX_VALUE),
                        LongStream.of(Long.MIN_VALUE, Integer.MIN_VALUE, (1L << 61) - 1, (1L << 62) - 57))
                .flatMapToLong(stream -> stream);
        // isProbablePrime answers for the absolute value.
        assertThat(values.filter(n -> LongMath.isPrime(n) != (n >= 2 && big(n).isProbablePrime(100))))
                .isEmpty();
    }

    @Test
    void findsCompositesThatPassSomeTests() {
        // Strong pseudoprimes to the smallest prime bases, from 2 to 7 up to 2 to 31, then Carmichael numbers.
        var composites = new long[] {
            3_215_031_751L,
            2_152_302_898_747L,
            3_474_749_660_383L,
            341_550_071_728_321L,
            3_825_123_056_546_413_051L,
            9_746_347_772_161L,
            1_436_697_831_295_441L
        };
        for (var n : composites) {
            assertThat(big(n).isProbablePrime(100)).as("%d", n).isFalse();
            assertThat(LongMath.isPrime(n)).as("%d", n).isFalse();
        }
        // Products of two primes, and squares of primes, too large for any small factor.
        var random = new SplittableRandom(6);
        var primes = random.ints(1 << 30, Integer.MAX_VALUE)
                .filter(IntMath::isPrime)
                .limit(2_000)
                .asLongStream()
                .toArray();
        for (int i = 0; i + 1 < primes.length; i += 2) {
            assertThat(LongMath.isPrime(primes[i] * primes[i + 1])).isFalse();
            assertThat(LongMath.isPrime(primes[i] * primes[i])).isFalse();
        }
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsANullRoundingMode() {
        assertThatNullPointerException()
                .isThrownBy(() -> LongMath.log2(1, null))
                .withMessage("mode");
        assertThatNullPointerException()
                .isThrownBy(() -> LongMath.log10(1, null))
                .withMessage("mode");
        assertThatNullPointerException()
                .isThrownBy(() -> LongMath.sqrt(1, null))
                .withMessage("mode");
        assertThatNullPointerException()
                .isThrownBy(() -> LongMath.divide(1, 1, null))
                .withMessage("mode");
    }
}
