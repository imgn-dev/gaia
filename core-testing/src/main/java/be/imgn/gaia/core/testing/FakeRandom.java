package be.imgn.gaia.core.testing;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static be.imgn.gaia.core.base.Preconditions.checkState;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * A {@link RandomGenerator} that gives the numbers a test scripted, in order, so that tests of code that uses
 * randomness are exact.
 *
 * <pre>{@code
 * var random = new FakeRandom().addFaces(6, 1, 4, 4);
 * var rolled = DiceExpression.parse("4d6dl1").roll(random);
 * assertThat(rolled.value()).isEqualTo(14);
 * assertThat(random.isExhausted()).isTrue();
 * }</pre>
 *
 * <p>A scripted number is of one of two kinds:
 *
 * <ul>
 *   <li>An exact value, added with one of the {@code add} methods, is given as it is to a call that returns its type:
 *       an {@code int} to {@code nextInt}, with or without bounds, a {@code long} to {@code nextLong}, a {@code double}
 *       to {@code nextDouble()}, and a {@code boolean} to {@code nextBoolean()}.
 *   <li>A fraction, added with {@link #addFractions(double...)}, is given to any call, as the value at that fraction of
 *       what the call may return: {@code 0.5} is {@code 3} to {@code nextInt(6)} and {@code 0.5} to
 *       {@code nextDouble()}. To {@code nextLong()}, it's the 64 bits that are at that fraction of 2^64 when read as an
 *       unsigned number, which is how a weighted {@code RandomSelector} reads them.
 * </ul>
 *
 * <p>A call fails with {@link IllegalStateException} when no number is left, when the next one is an exact value of
 * another type, or when it's out of the bounds of the call: the code under test doesn't use randomness as the test
 * expects. The other methods of {@link RandomGenerator} are built on these ones.
 *
 * <p>It isn't thread-safe.
 */
public final class FakeRandom implements RandomGenerator {

    private final Deque<Object> script = new ArrayDeque<>();

    /** Creates a source of randomness with no number, to add some to. */
    public FakeRandom() {}

    /**
     * Adds exact values for calls to {@code nextInt}.
     *
     * @param values the values to give
     * @return this source of randomness
     */
    public FakeRandom add(int... values) {
        for (int value : values) {
            script.add(value);
        }
        return this;
    }

    /**
     * Adds exact values for calls to {@code nextLong}.
     *
     * @param values the values to give
     * @return this source of randomness
     */
    public FakeRandom add(long... values) {
        for (long value : values) {
            script.add(value);
        }
        return this;
    }

    /**
     * Adds exact values for calls to {@code nextDouble()}.
     *
     * @param values the values to give
     * @return this source of randomness
     * @throws IllegalArgumentException if a value is less than zero, or isn't less than one
     */
    public FakeRandom add(double... values) {
        for (double value : values) {
            checkArgument(value >= 0 && value < 1, "Not between 0 and 1: %s", value);
            script.add(value);
        }
        return this;
    }

    /**
     * Adds exact values for calls to {@code nextBoolean()}.
     *
     * @param values the values to give
     * @return this source of randomness
     */
    public FakeRandom add(boolean... values) {
        for (boolean value : values) {
            script.add(value);
        }
        return this;
    }

    /**
     * Adds the faces that dice show: each one is given, minus one, to a call to {@code nextInt}, which is how a die
     * with faces numbered from one is rolled.
     *
     * @param faces the faces to show
     * @return this source of randomness
     * @throws IllegalArgumentException if a face is less than one
     */
    public FakeRandom addFaces(int... faces) {
        for (int face : faces) {
            checkArgument(face >= 1, "Not a face: %d", face);
            script.add(face - 1);
        }
        return this;
    }

    /**
     * Adds fractions, each given to any call as the value at that fraction of what the call may return.
     *
     * @param fractions the fractions to give
     * @return this source of randomness
     * @throws IllegalArgumentException if a fraction is less than zero, or isn't less than one
     */
    public FakeRandom addFractions(double... fractions) {
        for (double fraction : fractions) {
            checkArgument(fraction >= 0 && fraction < 1, "Not between 0 and 1: %s", fraction);
            script.add(new Fraction(fraction));
        }
        return this;
    }

    /**
     * Returns how many scripted numbers were not given yet.
     *
     * @return the number of numbers left
     */
    public int remaining() {
        return script.size();
    }

    /**
     * Returns whether every scripted number was given.
     *
     * @return whether no number is left
     */
    public boolean isExhausted() {
        return script.isEmpty();
    }

    @Override
    public int nextInt() {
        return (int) next("nextInt()", Integer.class, Integer.MIN_VALUE, Integer.MAX_VALUE + 1L);
    }

    @Override
    public int nextInt(int bound) {
        checkArgument(bound > 0, "bound must be positive: %d", bound);
        return (int) next("nextInt(" + bound + ")", Integer.class, 0, bound);
    }

    @Override
    public int nextInt(int origin, int bound) {
        checkArgument(origin < bound, "origin must be less than bound: %d, %d", origin, bound);
        return (int) next("nextInt(" + origin + ", " + bound + ")", Integer.class, origin, bound);
    }

    @Override
    public long nextLong() {
        var call = "nextLong()";
        var next = take(call);
        return switch (next) {
            case Long value -> value;
            // The 64 bits that, read as an unsigned number, are at that fraction of 2^64: code that takes its
            // randomness from the high bits of a long, as nextDouble() does by default, sees the fraction.
            case Fraction fraction ->
                scale(fraction, BigInteger.ONE.shiftLeft(Long.SIZE)).longValue();
            default -> throw mismatch(call, next);
        };
    }

    @Override
    public long nextLong(long bound) {
        checkArgument(bound > 0, "bound must be positive: %d", bound);
        return next("nextLong(" + bound + ")", Long.class, 0, bound);
    }

    @Override
    public long nextLong(long origin, long bound) {
        checkArgument(origin < bound, "origin must be less than bound: %d, %d", origin, bound);
        return next("nextLong(" + origin + ", " + bound + ")", Long.class, origin, bound);
    }

    @Override
    public double nextDouble() {
        var call = "nextDouble()";
        var next = take(call);
        return switch (next) {
            case Double value -> value;
            case Fraction fraction -> fraction.value;
            default -> throw mismatch(call, next);
        };
    }

    @Override
    public boolean nextBoolean() {
        var call = "nextBoolean()";
        var next = take(call);
        return switch (next) {
            case Boolean value -> value;
            case Fraction fraction -> fraction.value >= 1.0 / 2;
            default -> throw mismatch(call, next);
        };
    }

    /** Gives the next number to a call that returns a whole number from {@code origin} to below {@code bound}. */
    private long next(String call, Class<? extends Number> type, long origin, long bound) {
        var next = take(call);
        if (next instanceof Fraction fraction) {
            var range = BigInteger.valueOf(bound).subtract(BigInteger.valueOf(origin));
            return scale(fraction, range).add(BigInteger.valueOf(origin)).longValueExact();
        }
        if (!type.isInstance(next)) {
            throw mismatch(call, next);
        }
        long value = ((Number) next).longValue();
        checkState(value >= origin && value < bound, "%s was called, but the next number is %d", call, value);
        return value;
    }

    private Object take(String call) {
        var next = script.poll();
        checkState(next != null, "%s was called, but no number is left", call);
        return next;
    }

    private static BigInteger scale(Fraction fraction, BigInteger range) {
        // A double is an exact binary fraction, and so is its product by a whole number: no rounding but down.
        return new BigDecimal(fraction.value).multiply(new BigDecimal(range)).toBigInteger();
    }

    private static IllegalStateException mismatch(String call, Object next) {
        return new IllegalStateException(String.format(
                "%s was called, but the next number is the %s %s",
                call, next.getClass().getSimpleName().toLowerCase(Locale.ROOT), next));
    }

    /** A number given to any call, as a fraction of its range. */
    private record Fraction(double value) {

        @Override
        public String toString() {
            return Double.toString(value);
        }
    }
}
