package be.imgn.gaia.core.math;

import static java.util.Objects.requireNonNull;

import java.math.BigInteger;
import org.jspecify.annotations.Nullable;

/**
 * An exact fraction of two {@code int} values, always in lowest terms and with a positive denominator.
 *
 * <pre>{@code
 * var half = IntFraction.of(2, 4);            // 1/2
 * half.plus(IntFraction.of(1, 3));            // 5/6
 * half.times(IntFraction.of(-4));             // -2
 * half.compareTo(IntFraction.of(3, 5)) < 0;   // true
 * half.doubleValue();                         // 0.5
 * }</pre>
 *
 * <p>Two fractions are equal if and only if they have the same value, since each value has only one representation. A
 * method whose result, once in lowest terms, has a numerator or a denominator that doesn't fit in an {@code int} throws
 * {@link ArithmeticException}.
 *
 * @since 0.2.0
 * @see Fraction
 * @see LongFraction
 * @see BigFraction
 */
public final class IntFraction extends Fraction {

    /** The fraction 0/1. */
    public static final IntFraction ZERO = new IntFraction(0, 1);

    /** The fraction 1/1. */
    public static final IntFraction ONE = new IntFraction(1, 1);

    private static final long serialVersionUID = 1L;

    private final int numerator;
    private final int denominator;

    private IntFraction(int numerator, int denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    /**
     * Returns the fraction equal to a whole number.
     *
     * @param value the value
     * @return the fraction equal to a whole number
     */
    public static IntFraction of(int value) {
        return value == 0 ? ZERO : value == 1 ? ONE : new IntFraction(value, 1);
    }

    /**
     * Returns the fraction {@code numerator / denominator}, in lowest terms.
     *
     * @param numerator the numerator
     * @param denominator the denominator, which may be negative
     * @return the fraction {@code numerator / denominator}, in lowest terms
     * @throws ArithmeticException if {@code denominator} is zero, or if the fraction in lowest terms doesn't fit
     */
    public static IntFraction of(long numerator, long denominator) {
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
        if ((int) n != n || (int) d != d) {
            throw overflow(n + "/" + d);
        }
        return d == 1 ? of((int) n) : new IntFraction((int) n, (int) d);
    }

    /**
     * Returns the fraction {@code numerator / denominator}, in lowest terms.
     *
     * @param numerator the numerator
     * @param denominator the denominator, which may be negative
     * @return the fraction {@code numerator / denominator}, in lowest terms
     * @throws ArithmeticException if {@code denominator} is zero, or if the fraction in lowest terms doesn't fit
     */
    public static IntFraction of(BigInteger numerator, BigInteger denominator) {
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
        if (n.bitLength() >= Integer.SIZE || d.bitLength() >= Integer.SIZE) {
            throw overflow(n + "/" + d);
        }
        return of(n.intValue(), d.intValue());
    }

    private static ArithmeticException overflow(String fraction) {
        return new ArithmeticException("Fraction overflow: " + fraction);
    }

    /**
     * Returns the numerator, which has the sign of this fraction.
     *
     * @return the numerator, which has the sign of this fraction
     */
    public int numerator() {
        return numerator;
    }

    /**
     * Returns the denominator, which is positive.
     *
     * @return the denominator, which is positive
     */
    public int denominator() {
        return denominator;
    }

    /**
     * Returns -1, 0 or 1 as this fraction is negative, zero or positive.
     *
     * @return -1, 0 or 1 as this fraction is negative, zero or positive
     */
    @Override
    public int signum() {
        return Integer.signum(numerator);
    }

    /**
     * Returns whether this fraction is a whole number.
     *
     * @return whether this fraction is a whole number
     */
    @Override
    public boolean isInteger() {
        return denominator == 1;
    }

    /**
     * Returns the sum of this fraction and another one.
     *
     * @param other the fraction to add
     * @return the sum of this fraction and another one
     * @throws ArithmeticException if the result doesn't fit
     */
    public IntFraction plus(IntFraction other) {
        return of(
                (long) numerator * other.denominator + (long) other.numerator * denominator,
                (long) denominator * other.denominator);
    }

    /**
     * Returns the difference between this fraction and another one.
     *
     * @param other the fraction to subtract
     * @return the difference between this fraction and another one
     * @throws ArithmeticException if the result doesn't fit
     */
    public IntFraction minus(IntFraction other) {
        return of(
                (long) numerator * other.denominator - (long) other.numerator * denominator,
                (long) denominator * other.denominator);
    }

    /**
     * Returns the product of this fraction and another one.
     *
     * @param other the fraction to multiply by
     * @return the product of this fraction and another one
     * @throws ArithmeticException if the result doesn't fit
     */
    public IntFraction times(IntFraction other) {
        return of((long) numerator * other.numerator, (long) denominator * other.denominator);
    }

    /**
     * Returns the quotient of this fraction by another one.
     *
     * @param other the fraction to divide by
     * @return the quotient of this fraction by another one
     * @throws ArithmeticException if {@code other} is zero, or if the result doesn't fit
     */
    public IntFraction dividedBy(IntFraction other) {
        if (other.numerator == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return of((long) numerator * other.denominator, (long) denominator * other.numerator);
    }

    /**
     * Returns the opposite of this fraction.
     *
     * @return the opposite of this fraction
     * @throws ArithmeticException if the numerator is {@link Integer#MIN_VALUE}
     */
    public IntFraction negate() {
        return of(-(long) numerator, denominator);
    }

    /**
     * Returns the absolute value of this fraction.
     *
     * @return the absolute value of this fraction
     * @throws ArithmeticException if the numerator is {@link Integer#MIN_VALUE}
     */
    public IntFraction abs() {
        return numerator < 0 ? negate() : this;
    }

    /**
     * Returns the fraction that this one multiplies to one with.
     *
     * @return the fraction that this one multiplies to one with
     * @throws ArithmeticException if this fraction is zero, or if the numerator is {@link Integer#MIN_VALUE}
     */
    public IntFraction reciprocal() {
        if (numerator == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return of(denominator, numerator);
    }

    @Override
    public IntFraction toIntFractionExact() {
        return this;
    }

    @Override
    public LongFraction toLongFractionExact() {
        return toLongFraction();
    }

    /**
     * Returns this fraction as a {@link LongFraction}.
     *
     * @return the same fraction
     */
    public LongFraction toLongFraction() {
        return LongFraction.of(numerator, denominator);
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
     * Returns this fraction rounded toward zero.
     *
     * @return this fraction rounded toward zero
     */
    @Override
    public int intValue() {
        return numerator / denominator;
    }

    /**
     * Returns this fraction rounded toward zero.
     *
     * @return this fraction rounded toward zero
     */
    @Override
    public long longValue() {
        return intValue();
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
     * Returns the {@code double} nearest to this fraction.
     *
     * @return the {@code double} nearest to this fraction
     */
    @Override
    public double doubleValue() {
        // Both terms are exact as doubles, and a division is correctly rounded.
        return (double) numerator / denominator;
    }

    @Override
    public int compareTo(Fraction other) {
        return other instanceof IntFraction same
                ? Long.compare((long) numerator * same.denominator, (long) same.numerator * denominator)
                : toBigFraction().compareTo(other);
    }

    @Override
    public boolean equals(@Nullable Object object) {
        return object instanceof IntFraction other && numerator == other.numerator && denominator == other.denominator;
    }

    @Override
    public int hashCode() {
        return 31 * numerator + denominator;
    }

    /**
     * Returns {@code "numerator/denominator"}, or the numerator alone if this fraction is a whole number.
     *
     * @return {@code "numerator/denominator"}, or the numerator alone if this fraction is a whole number
     */
    @Override
    public String toString() {
        return denominator == 1 ? Integer.toString(numerator) : numerator + "/" + denominator;
    }
}
