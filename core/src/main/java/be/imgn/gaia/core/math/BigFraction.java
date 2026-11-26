package be.imgn.gaia.core.math;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import org.jspecify.annotations.Nullable;

/**
 * An exact fraction of two {@link BigInteger} values, always in lowest terms and with a positive denominator. It never
 * overflows.
 *
 * <pre>{@code
 * var third = BigFraction.of(1, 3);
 * third.pow(40);                                // 1/12157665459056928801
 * third.plus(BigFraction.of(1, 6));             // 1/2
 * BigFraction.of(0.1);                          // 3602879701896397/36028797018963968, what the double is exactly
 * }</pre>
 *
 * <p>Two fractions are equal if and only if they have the same value, since each value has only one representation.
 *
 * @since 0.2.0
 * @see IntFraction
 * @see LongFraction
 */
public final class BigFraction extends Number implements Comparable<BigFraction> {

    /** The fraction 0/1. */
    public static final BigFraction ZERO = new BigFraction(BigInteger.ZERO, BigInteger.ONE);

    /** The fraction 1/1. */
    public static final BigFraction ONE = new BigFraction(BigInteger.ONE, BigInteger.ONE);

    private static final long serialVersionUID = 1L;

    private final BigInteger numerator;
    private final BigInteger denominator;

    private BigFraction(BigInteger numerator, BigInteger denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    /**
     * Returns the fraction equal to a whole number.
     *
     * @param value the value
     * @return the fraction {@code value / 1}
     */
    public static BigFraction of(long value) {
        return of(BigInteger.valueOf(value));
    }

    /**
     * Returns the fraction equal to a whole number.
     *
     * @param value the value
     * @return the fraction {@code value / 1}
     */
    public static BigFraction of(BigInteger value) {
        return value.signum() == 0 ? ZERO : new BigFraction(value, BigInteger.ONE);
    }

    /**
     * Returns the fraction {@code numerator / denominator}, in lowest terms.
     *
     * @param numerator the numerator
     * @param denominator the denominator, which may be negative
     * @return the fraction
     * @throws ArithmeticException if {@code denominator} is zero
     */
    public static BigFraction of(long numerator, long denominator) {
        return of(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    /**
     * Returns the fraction {@code numerator / denominator}, in lowest terms.
     *
     * @param numerator the numerator
     * @param denominator the denominator, which may be negative
     * @return the fraction
     * @throws ArithmeticException if {@code denominator} is zero
     */
    public static BigFraction of(BigInteger numerator, BigInteger denominator) {
        requireNonNull(numerator, "numerator");
        if (denominator.signum() == 0) {
            throw new ArithmeticException("Zero denominator");
        }
        if (numerator.signum() == 0) {
            return ZERO;
        }
        var gcd = numerator.gcd(denominator);
        var n = numerator.divide(gcd);
        var d = denominator.divide(gcd);
        return d.signum() < 0 ? new BigFraction(n.negate(), d.negate()) : new BigFraction(n, d);
    }

    /**
     * Returns the fraction that a {@code double} is exactly: every finite {@code double} is a whole number over a power
     * of two.
     *
     * @param value the value
     * @return the fraction equal to {@code value}
     * @throws ArithmeticException if {@code value} is infinite or not a number
     */
    public static BigFraction of(double value) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Not a finite number: " + value);
        }
        var decimal = new BigDecimal(value);
        return decimal.scale() <= 0
                ? of(decimal.toBigIntegerExact())
                : of(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()));
    }

    /**
     * Returns the numerator, which has the sign of this fraction.
     *
     * @return the numerator
     */
    public BigInteger numerator() {
        return numerator;
    }

    /**
     * Returns the denominator, which is positive.
     *
     * @return the denominator
     */
    public BigInteger denominator() {
        return denominator;
    }

    /**
     * Returns -1, 0 or 1 as this fraction is negative, zero or positive.
     *
     * @return the sign of this fraction
     */
    public int signum() {
        return numerator.signum();
    }

    /**
     * Returns whether this fraction is a whole number.
     *
     * @return whether the denominator is one
     */
    public boolean isInteger() {
        return denominator.equals(BigInteger.ONE);
    }

    /**
     * Returns the sum of this fraction and another one.
     *
     * @param other the fraction to add
     * @return the sum
     */
    public BigFraction plus(BigFraction other) {
        return of(
                numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    /**
     * Returns the difference between this fraction and another one.
     *
     * @param other the fraction to subtract
     * @return the difference
     */
    public BigFraction minus(BigFraction other) {
        return of(
                numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    /**
     * Returns the product of this fraction and another one.
     *
     * @param other the fraction to multiply by
     * @return the product
     */
    public BigFraction times(BigFraction other) {
        return of(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
    }

    /**
     * Returns the quotient of this fraction by another one.
     *
     * @param other the fraction to divide by
     * @return the quotient
     * @throws ArithmeticException if {@code other} is zero
     */
    public BigFraction dividedBy(BigFraction other) {
        if (other.numerator.signum() == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return of(numerator.multiply(other.denominator), denominator.multiply(other.numerator));
    }

    /**
     * Returns this fraction raised to a power.
     *
     * @param exponent the exponent, which may be negative
     * @return this fraction multiplied by itself {@code exponent} times, or the reciprocal of that
     * @throws ArithmeticException if this fraction is zero and {@code exponent} is negative
     */
    public BigFraction pow(int exponent) {
        if (exponent < 0) {
            // The exponent of Integer.MIN_VALUE has no opposite: one factor is taken apart.
            return exponent == Integer.MIN_VALUE
                    ? reciprocal().pow(Integer.MAX_VALUE).times(reciprocal())
                    : reciprocal().pow(-exponent);
        }
        // Powers of numbers with no common factor have none either.
        return new BigFraction(numerator.pow(exponent), denominator.pow(exponent)).normalized();
    }

    private BigFraction normalized() {
        return numerator.signum() == 0 ? ZERO : this;
    }

    /**
     * Returns the opposite of this fraction.
     *
     * @return the opposite
     */
    public BigFraction negate() {
        return numerator.signum() == 0 ? this : new BigFraction(numerator.negate(), denominator);
    }

    /**
     * Returns the absolute value of this fraction.
     *
     * @return the absolute value
     */
    public BigFraction abs() {
        return numerator.signum() < 0 ? negate() : this;
    }

    /**
     * Returns the fraction that this one multiplies to one with.
     *
     * @return the reciprocal
     * @throws ArithmeticException if this fraction is zero
     */
    public BigFraction reciprocal() {
        if (numerator.signum() == 0) {
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
    public IntFraction toIntFractionExact() {
        return IntFraction.of(numerator, denominator);
    }

    /**
     * Returns this fraction as a {@link LongFraction}.
     *
     * @return the same fraction
     * @throws ArithmeticException if the numerator or the denominator doesn't fit in a {@code long}
     */
    public LongFraction toLongFractionExact() {
        return LongFraction.of(numerator, denominator);
    }

    /**
     * Returns this fraction rounded toward zero, then narrowed as {@link BigInteger#intValue()} does.
     *
     * @return this fraction as an {@code int}
     */
    @Override
    public int intValue() {
        return numerator.divide(denominator).intValue();
    }

    /**
     * Returns this fraction rounded toward zero, then narrowed as {@link BigInteger#longValue()} does.
     *
     * @return this fraction as a {@code long}
     */
    @Override
    public long longValue() {
        return numerator.divide(denominator).longValue();
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
        return new BigDecimal(numerator)
                .divide(new BigDecimal(denominator), MathContext.DECIMAL128)
                .doubleValue();
    }

    @Override
    public int compareTo(BigFraction other) {
        return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
    }

    @Override
    public boolean equals(@Nullable Object object) {
        return object instanceof BigFraction other
                && numerator.equals(other.numerator)
                && denominator.equals(other.denominator);
    }

    @Override
    public int hashCode() {
        return 31 * numerator.hashCode() + denominator.hashCode();
    }

    /**
     * Returns {@code "numerator/denominator"}, or the numerator alone if this fraction is a whole number.
     *
     * @return this fraction as a text
     */
    @Override
    public String toString() {
        return isInteger() ? numerator.toString() : numerator + "/" + denominator;
    }
}
