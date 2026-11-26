package be.imgn.gaia.core.math;

import static java.util.Objects.requireNonNull;

import java.math.BigInteger;
import org.jspecify.annotations.Nullable;

/**
 * An exact fraction of two {@code long} values, always in lowest terms and with a positive denominator.
 *
 * <pre>{@code
 * var half = LongFraction.of(2, 4);            // 1/2
 * half.plus(LongFraction.of(1, 3));            // 5/6
 * half.times(LongFraction.of(-4));             // -2
 * half.compareTo(LongFraction.of(3, 5)) < 0;   // true
 * }</pre>
 *
 * <p>Two fractions are equal if and only if they have the same value, since each value has only one representation. A
 * method whose result, once in lowest terms, has a numerator or a denominator that doesn't fit in a {@code long} throws
 * {@link ArithmeticException}; intermediate results are as large as they need to be.
 *
 * @since 0.2.0
 * @see Fraction
 * @see IntFraction
 * @see BigFraction
 */
public final class LongFraction extends Fraction {

    /** The fraction 0/1. */
    public static final LongFraction ZERO = new LongFraction(0, 1);

    /** The fraction 1/1. */
    public static final LongFraction ONE = new LongFraction(1, 1);

    private static final long serialVersionUID = 1L;

    // Whole numbers up to this one, in absolute value, are exact as doubles.
    private static final long MAX_EXACT_DOUBLE = 1L << 53;

    private final long numerator;
    private final long denominator;

    private LongFraction(long numerator, long denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    /**
     * Returns the fraction equal to a whole number.
     *
     * @param value the value
     * @return the fraction {@code value / 1}
     */
    public static LongFraction of(long value) {
        return value == 0 ? ZERO : value == 1 ? ONE : new LongFraction(value, 1);
    }

    /**
     * Returns the fraction {@code numerator / denominator}, in lowest terms.
     *
     * @param numerator the numerator
     * @param denominator the denominator, which may be negative
     * @return the fraction
     * @throws ArithmeticException if {@code denominator} is zero, or if the fraction in lowest terms doesn't fit
     */
    public static LongFraction of(long numerator, long denominator) {
        if (denominator == 0) {
            throw new ArithmeticException("Zero denominator");
        }
        if (numerator == Long.MIN_VALUE || denominator == Long.MIN_VALUE) {
            return of(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
        }
        long gcd = LongMath.gcd(numerator, denominator);
        long n = numerator / gcd;
        long d = denominator / gcd;
        if (d < 0) {
            n = -n;
            d = -d;
        }
        return d == 1 ? of(n) : new LongFraction(n, d);
    }

    /**
     * Returns the fraction {@code numerator / denominator}, in lowest terms.
     *
     * @param numerator the numerator
     * @param denominator the denominator, which may be negative
     * @return the fraction
     * @throws ArithmeticException if {@code denominator} is zero, or if the fraction in lowest terms doesn't fit
     */
    public static LongFraction of(BigInteger numerator, BigInteger denominator) {
        requireNonNull(numerator, "numerator");
        if (denominator.signum() == 0) {
            throw new ArithmeticException("Zero denominator");
        }
        var gcd = numerator.gcd(denominator);
        var n = numerator.divide(gcd);
        var d = denominator.divide(gcd);
        if (d.signum() < 0) {
            n = n.negate();
            d = d.negate();
        }
        if (n.bitLength() >= Long.SIZE || d.bitLength() >= Long.SIZE) {
            throw new ArithmeticException("Fraction overflow: " + n + "/" + d);
        }
        long numeratorValue = n.longValue();
        long denominatorValue = d.longValue();
        return denominatorValue == 1 ? of(numeratorValue) : new LongFraction(numeratorValue, denominatorValue);
    }

    /**
     * Returns the numerator, which has the sign of this fraction.
     *
     * @return the numerator
     */
    public long numerator() {
        return numerator;
    }

    /**
     * Returns the denominator, which is positive.
     *
     * @return the denominator
     */
    public long denominator() {
        return denominator;
    }

    /**
     * Returns -1, 0 or 1 as this fraction is negative, zero or positive.
     *
     * @return the sign of this fraction
     */
    @Override
    public int signum() {
        return Long.signum(numerator);
    }

    /**
     * Returns whether this fraction is a whole number.
     *
     * @return whether the denominator is one
     */
    @Override
    public boolean isInteger() {
        return denominator == 1;
    }

    /**
     * Returns the sum of this fraction and another one.
     *
     * @param other the fraction to add
     * @return the sum
     * @throws ArithmeticException if the result doesn't fit
     */
    public LongFraction plus(LongFraction other) {
        return toBigFraction().plus(other.toBigFraction()).toLongFractionExact();
    }

    /**
     * Returns the difference between this fraction and another one.
     *
     * @param other the fraction to subtract
     * @return the difference
     * @throws ArithmeticException if the result doesn't fit
     */
    public LongFraction minus(LongFraction other) {
        return toBigFraction().minus(other.toBigFraction()).toLongFractionExact();
    }

    /**
     * Returns the product of this fraction and another one.
     *
     * @param other the fraction to multiply by
     * @return the product
     * @throws ArithmeticException if the result doesn't fit
     */
    public LongFraction times(LongFraction other) {
        return toBigFraction().times(other.toBigFraction()).toLongFractionExact();
    }

    /**
     * Returns the quotient of this fraction by another one.
     *
     * @param other the fraction to divide by
     * @return the quotient
     * @throws ArithmeticException if {@code other} is zero, or if the result doesn't fit
     */
    public LongFraction dividedBy(LongFraction other) {
        return toBigFraction().dividedBy(other.toBigFraction()).toLongFractionExact();
    }

    /**
     * Returns the opposite of this fraction.
     *
     * @return the opposite
     * @throws ArithmeticException if the numerator is {@link Long#MIN_VALUE}
     */
    public LongFraction negate() {
        return new LongFraction(Math.negateExact(numerator), denominator);
    }

    /**
     * Returns the absolute value of this fraction.
     *
     * @return the absolute value
     * @throws ArithmeticException if the numerator is {@link Long#MIN_VALUE}
     */
    public LongFraction abs() {
        return numerator < 0 ? negate() : this;
    }

    /**
     * Returns the fraction that this one multiplies to one with.
     *
     * @return the reciprocal
     * @throws ArithmeticException if this fraction is zero, or if the numerator is {@link Long#MIN_VALUE}
     */
    public LongFraction reciprocal() {
        if (numerator == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return of(denominator, numerator);
    }

    /**
     * Returns this fraction as an {@link IntFraction}.
     *
     * @return the same fraction
     * @throws ArithmeticException if the numerator or the denominator doesn't fit in an {@code int}
     */
    @Override
    public IntFraction toIntFractionExact() {
        return IntFraction.of(numerator, denominator);
    }

    @Override
    public LongFraction toLongFractionExact() {
        return this;
    }

    /**
     * Returns this fraction as a {@link BigFraction}.
     *
     * @return the same fraction
     */
    @Override
    public BigFraction toBigFraction() {
        return BigFraction.of(numerator, denominator);
    }

    /**
     * Returns this fraction rounded toward zero, then narrowed to an {@code int}.
     *
     * @return this fraction as an {@code int}
     */
    @Override
    public int intValue() {
        return (int) longValue();
    }

    /**
     * Returns this fraction rounded toward zero.
     *
     * @return this fraction as a {@code long}
     */
    @Override
    public long longValue() {
        return numerator / denominator;
    }

    /**
     * Returns this fraction as a {@code float}.
     *
     * @return this fraction as a {@code float}
     */
    @Override
    public float floatValue() {
        return (float) doubleValue();
    }

    /**
     * Returns a {@code double} close to this fraction: the nearest one, or one next to it.
     *
     * @return this fraction as a {@code double}
     */
    @Override
    public double doubleValue() {
        // When both terms are exact as doubles, their division is correctly rounded.
        return Math.abs(numerator) <= MAX_EXACT_DOUBLE && denominator <= MAX_EXACT_DOUBLE
                ? (double) numerator / denominator
                : toBigFraction().doubleValue();
    }

    @Override
    public int compareTo(Fraction other) {
        if (!(other instanceof LongFraction same)) {
            return toBigFraction().compareTo(other);
        }
        // The products are of 128 bits: their high halves, then their low halves as unsigned numbers.
        long thisHigh = Math.multiplyHigh(numerator, same.denominator);
        long otherHigh = Math.multiplyHigh(same.numerator, denominator);
        return thisHigh != otherHigh
                ? Long.compare(thisHigh, otherHigh)
                : Long.compareUnsigned(numerator * same.denominator, same.numerator * denominator);
    }

    @Override
    public boolean equals(@Nullable Object object) {
        return object instanceof LongFraction other && numerator == other.numerator && denominator == other.denominator;
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(numerator) + Long.hashCode(denominator);
    }

    /**
     * Returns {@code "numerator/denominator"}, or the numerator alone if this fraction is a whole number.
     *
     * @return this fraction as a text
     */
    @Override
    public String toString() {
        return denominator == 1 ? Long.toString(numerator) : numerator + "/" + denominator;
    }
}
