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
 * <p>This class has what the three have in common whatever their size: comparing, converting, and the methods of
 * {@link Number}. Arithmetic is on each class, with fractions of the same class.
 *
 * <p>Fractions of any classes compare by value, but only fractions of the same class are equal, as an {@code Integer}
 * never equals a {@code Long}: {@code compareTo} may return zero for fractions that {@code equals} tells apart.
 *
 * @since 0.2.0
 */
public abstract sealed class Fraction extends Number implements Comparable<Fraction>
        permits IntFraction, LongFraction, BigFraction {

    private static final long serialVersionUID = 1L;

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
     * Compares this fraction with another one of any class, by value.
     *
     * @param other the fraction to compare with
     * @return a negative number, zero or a positive number as this fraction is less than, equal to or greater than the
     *     other one
     */
    @Override
    public abstract int compareTo(Fraction other);
}
