package be.imgn.gaia.core.math;

/**
 * An exact fraction, always in lowest terms and with a positive denominator: an {@link IntFraction}, a
 * {@link LongFraction} or a {@link BigFraction}, which differ by the size of their terms.
 *
 * <pre>{@code
 * Fraction chance = chances.of(7);                        // an IntFraction
 * chance.compareTo(BigFraction.of(1, 6)) == 0;            // true: fractions of any size compare by value
 * chance.toBigFraction().pow(3);                          // 1/216
 * chance.doubleValue();                                   // 0.16666666666666666
 * }</pre>
 *
 * <p>This class has what the three have in common whatever their size: comparing, converting, arithmetic, and the
 * methods of {@link Number}.
 *
 * <p>An operation on two fractions returns a fraction of the larger of their two classes: an {@link IntFraction} and a
 * {@link LongFraction} give a {@link LongFraction}. Each class also has the same operations on fractions of its own
 * class, which return that class, so that the result of {@code IntFraction.plus(IntFraction)} is known to be an
 * {@link IntFraction}. A result that doesn't fit in its class throws {@link ArithmeticException}: it isn't given a
 * larger class.
 *
 * <p>Fractions of any classes compare by value, but only fractions of the same class are equal, as an {@code Integer}
 * never equals a {@code Long}: {@code compareTo} may return zero for fractions that {@code equals} tells apart.
 *
 * @since 0.2.0
 */
public abstract sealed class Fraction extends Number implements Comparable<Fraction>
        permits IntFraction, LongFraction, BigFraction {

    private static final long serialVersionUID = 1L;

    private static final int INT = 0;
    private static final int LONG = 1;
    private static final int BIG = 2;

    Fraction() {}

    /**
     * Returns -1, 0 or 1 as this fraction is negative, zero or positive.
     *
     * @return the sign of this fraction
     */
    public abstract int signum();

    /**
     * Returns whether this fraction is a whole number.
     *
     * @return whether the denominator is one
     */
    public abstract boolean isInteger();

    /**
     * Returns this fraction as an {@link IntFraction}.
     *
     * @return the same fraction
     * @throws ArithmeticException if the numerator or the denominator doesn't fit in an {@code int}
     */
    public abstract IntFraction toIntFractionExact();

    /**
     * Returns this fraction as a {@link LongFraction}.
     *
     * @return the same fraction
     * @throws ArithmeticException if the numerator or the denominator doesn't fit in a {@code long}
     */
    public abstract LongFraction toLongFractionExact();

    /**
     * Returns this fraction as a {@link BigFraction}.
     *
     * @return the same fraction
     */
    public abstract BigFraction toBigFraction();

    /**
     * Returns the sum of this fraction and another one.
     *
     * @param other the fraction to add
     * @return the sum, of the larger class of the two fractions
     * @throws ArithmeticException if the result doesn't fit in its class
     */
    public Fraction plus(Fraction other) {
        return switch (Math.max(size(), other.size())) {
            case INT -> toIntFractionExact().plus(other.toIntFractionExact());
            case LONG -> toLongFractionExact().plus(other.toLongFractionExact());
            default -> toBigFraction().plus(other.toBigFraction());
        };
    }

    /**
     * Returns the difference between this fraction and another one.
     *
     * @param other the fraction to subtract
     * @return the difference, of the larger class of the two fractions
     * @throws ArithmeticException if the result doesn't fit in its class
     */
    public Fraction minus(Fraction other) {
        return switch (Math.max(size(), other.size())) {
            case INT -> toIntFractionExact().minus(other.toIntFractionExact());
            case LONG -> toLongFractionExact().minus(other.toLongFractionExact());
            default -> toBigFraction().minus(other.toBigFraction());
        };
    }

    /**
     * Returns the product of this fraction and another one.
     *
     * @param other the fraction to multiply by
     * @return the product, of the larger class of the two fractions
     * @throws ArithmeticException if the result doesn't fit in its class
     */
    public Fraction times(Fraction other) {
        return switch (Math.max(size(), other.size())) {
            case INT -> toIntFractionExact().times(other.toIntFractionExact());
            case LONG -> toLongFractionExact().times(other.toLongFractionExact());
            default -> toBigFraction().times(other.toBigFraction());
        };
    }

    /**
     * Returns the quotient of this fraction by another one.
     *
     * @param other the fraction to divide by
     * @return the quotient, of the larger class of the two fractions
     * @throws ArithmeticException if {@code other} is zero, or if the result doesn't fit in its class
     */
    public Fraction dividedBy(Fraction other) {
        return switch (Math.max(size(), other.size())) {
            case INT -> toIntFractionExact().dividedBy(other.toIntFractionExact());
            case LONG -> toLongFractionExact().dividedBy(other.toLongFractionExact());
            default -> toBigFraction().dividedBy(other.toBigFraction());
        };
    }

    /**
     * Returns the opposite of this fraction.
     *
     * @return the opposite, of the class of this fraction
     * @throws ArithmeticException if the result doesn't fit in its class
     */
    public abstract Fraction negate();

    /**
     * Returns the absolute value of this fraction.
     *
     * @return the absolute value, of the class of this fraction
     * @throws ArithmeticException if the result doesn't fit in its class
     */
    public abstract Fraction abs();

    /**
     * Returns the fraction that this one multiplies to one with.
     *
     * @return the reciprocal, of the class of this fraction
     * @throws ArithmeticException if this fraction is zero, or if the result doesn't fit in its class
     */
    public abstract Fraction reciprocal();

    /** The rank of the class of this fraction among the three, from the smallest. */
    private int size() {
        return switch (this) {
            case IntFraction small -> INT;
            case LongFraction medium -> LONG;
            case BigFraction big -> BIG;
        };
    }

    /**
     * Compares this fraction with another one of any class, by value.
     *
     * @param other the fraction to compare with
     * @return a negative number, zero or a positive number as this fraction is less than, equal to or greater than the
     *     other one
     */
    @Override
    public abstract int compareTo(Fraction other);
}
