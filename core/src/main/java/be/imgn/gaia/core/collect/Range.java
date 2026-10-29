package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.util.Optional;

/**
 * An interval of comparable values: all the values between a lower and an upper bound. Each bound is either
 * {@linkplain Bound.Closed closed}, and includes its endpoint, {@linkplain Bound.Open open}, and excludes it, or
 * {@linkplain Bound.Unbounded absent}, and lets the range extend forever on that side.
 *
 * <pre>{@code
 * Range<Integer> digits = Range.closedOpen(0, 10);
 * digits.contains(9);  // true
 * digits.contains(10); // false
 * digits.toString();   // [0, 10)
 * }</pre>
 *
 * <p>The values are compared with their {@link Comparable#compareTo compareTo} method, which must be consistent: a
 * range contains a value if it compares between the endpoints, whether it equals them or not.
 *
 * <p>A range may be empty when its endpoints are equal and one bound is open, as in {@code [3, 3)}. Its lower bound
 * cannot be above its upper bound, and both bounds cannot be open with equal endpoints, as in {@code (3, 3)}.
 *
 * <p>Ranges are unmodifiable, and serializable if their endpoints are. Two ranges are equal if their bounds are, so two
 * empty ranges may differ: {@code [3, 3)} does not equal {@code (3, 3]}.
 *
 * @param <C> the type of the values
 * @param lower the lower bound
 * @param upper the upper bound
 * @since 0.1.2
 */
public record Range<C extends Comparable<? super C>>(Bound<C> lower, Bound<C> upper) implements Serializable {

    /**
     * Creates a range from its bounds.
     *
     * @param lower the lower bound
     * @param upper the upper bound
     * @throws IllegalArgumentException if the lower bound is above the upper bound, or if both are open with equal
     *     endpoints
     */
    public Range {
        requireNonNull(lower, "lower");
        requireNonNull(upper, "upper");
        if (compare(lower, true, upper, false) > 0) {
            // Only (a, a) gets here with equal endpoints: [a, a), (a, a] and [a, a] are valid.
            String reason = lower instanceof Bound.Open<?>
                            && upper instanceof Bound.Open<?>
                            && endpoint(lower).compareTo(endpoint(upper)) == 0
                    ? "an open range needs distinct endpoints"
                    : "the lower bound is above the upper bound";
            throw new IllegalArgumentException("invalid range " + format(lower, upper) + ": " + reason);
        }
    }

    /**
     * Returns the range of the values between two endpoints, both included: {@code [lower, upper]}.
     *
     * @param <C> the type of the values
     * @param lower the lower endpoint
     * @param upper the upper endpoint
     * @return the range {@code [lower, upper]}
     * @throws IllegalArgumentException if {@code lower} is greater than {@code upper}
     */
    public static <C extends Comparable<? super C>> Range<C> closed(C lower, C upper) {
        return new Range<>(new Bound.Closed<>(lower), new Bound.Closed<>(upper));
    }

    /**
     * Returns the range of the values between two endpoints, both excluded: {@code (lower, upper)}.
     *
     * @param <C> the type of the values
     * @param lower the lower endpoint
     * @param upper the upper endpoint
     * @return the range {@code (lower, upper)}
     * @throws IllegalArgumentException if {@code lower} is greater than or equal to {@code upper}
     */
    public static <C extends Comparable<? super C>> Range<C> open(C lower, C upper) {
        return new Range<>(new Bound.Open<>(lower), new Bound.Open<>(upper));
    }

    /**
     * Returns the range of the values between two endpoints, the lower one included: {@code [lower, upper)}.
     *
     * @param <C> the type of the values
     * @param lower the lower endpoint
     * @param upper the upper endpoint
     * @return the range {@code [lower, upper)}, empty if the endpoints are equal
     * @throws IllegalArgumentException if {@code lower} is greater than {@code upper}
     */
    public static <C extends Comparable<? super C>> Range<C> closedOpen(C lower, C upper) {
        return new Range<>(new Bound.Closed<>(lower), new Bound.Open<>(upper));
    }

    /**
     * Returns the range of the values between two endpoints, the upper one included: {@code (lower, upper]}.
     *
     * @param <C> the type of the values
     * @param lower the lower endpoint
     * @param upper the upper endpoint
     * @return the range {@code (lower, upper]}, empty if the endpoints are equal
     * @throws IllegalArgumentException if {@code lower} is greater than {@code upper}
     */
    public static <C extends Comparable<? super C>> Range<C> openClosed(C lower, C upper) {
        return new Range<>(new Bound.Open<>(lower), new Bound.Closed<>(upper));
    }

    /**
     * Returns the range of the values greater than or equal to an endpoint: {@code [lower, +∞)}.
     *
     * @param <C> the type of the values
     * @param lower the lower endpoint
     * @return the range {@code [lower, +∞)}
     */
    public static <C extends Comparable<? super C>> Range<C> atLeast(C lower) {
        return new Range<>(new Bound.Closed<>(lower), new Bound.Unbounded<>());
    }

    /**
     * Returns the range of the values greater than an endpoint: {@code (lower, +∞)}.
     *
     * @param <C> the type of the values
     * @param lower the lower endpoint
     * @return the range {@code (lower, +∞)}
     */
    public static <C extends Comparable<? super C>> Range<C> greaterThan(C lower) {
        return new Range<>(new Bound.Open<>(lower), new Bound.Unbounded<>());
    }

    /**
     * Returns the range of the values less than or equal to an endpoint: {@code (-∞, upper]}.
     *
     * @param <C> the type of the values
     * @param upper the upper endpoint
     * @return the range {@code (-∞, upper]}
     */
    public static <C extends Comparable<? super C>> Range<C> atMost(C upper) {
        return new Range<>(new Bound.Unbounded<>(), new Bound.Closed<>(upper));
    }

    /**
     * Returns the range of the values less than an endpoint: {@code (-∞, upper)}.
     *
     * @param <C> the type of the values
     * @param upper the upper endpoint
     * @return the range {@code (-∞, upper)}
     */
    public static <C extends Comparable<? super C>> Range<C> lessThan(C upper) {
        return new Range<>(new Bound.Unbounded<>(), new Bound.Open<>(upper));
    }

    /**
     * Returns the range of all values: {@code (-∞, +∞)}.
     *
     * @param <C> the type of the values
     * @return the range {@code (-∞, +∞)}
     */
    public static <C extends Comparable<? super C>> Range<C> all() {
        return new Range<>(new Bound.Unbounded<C>(), new Bound.Unbounded<C>());
    }

    /**
     * Returns whether this range contains a value.
     *
     * @param value the value
     * @return {@code true} if {@code value} is between the bounds of this range
     */
    public boolean contains(C value) {
        requireNonNull(value, "value");
        return compareToValue(lower, true, value) < 0 && compareToValue(upper, false, value) > 0;
    }

    /**
     * Returns whether this range contains no value, as {@code [3, 3)} does.
     *
     * @return {@code true} if this range is empty
     */
    public boolean isEmpty() {
        return compare(lower, true, upper, false) == 0;
    }

    /**
     * Returns whether this range contains all the values of another range, possibly an empty one within its bounds.
     *
     * @param other the other range
     * @return {@code true} if the bounds of {@code other} are within the bounds of this range
     */
    public boolean encloses(Range<C> other) {
        requireNonNull(other, "other");
        return compare(lower, true, other.lower, true) <= 0 && compare(other.upper, false, upper, false) <= 0;
    }

    /**
     * Returns whether the union of this range and another one is a range: whether they overlap or are adjacent, as
     * {@code [1, 3)} and {@code [3, 5]} are.
     *
     * @param other the other range
     * @return {@code true} if no value lies between the two ranges
     */
    public boolean isConnected(Range<C> other) {
        requireNonNull(other, "other");
        return compare(lower, true, other.upper, false) <= 0 && compare(other.lower, true, upper, false) <= 0;
    }

    /**
     * Returns the values that this range and another one have in common.
     *
     * @param other the other range
     * @return the largest range enclosed by both ranges, empty if they are adjacent, as {@code [1, 3)} and {@code [3,
     *     5]} are; or nothing if they are not {@linkplain #isConnected connected}
     */
    public Optional<Range<C>> intersection(Range<C> other) {
        requireNonNull(other, "other");
        if (!isConnected(other)) {
            return Optional.empty();
        }
        var newLower = compare(lower, true, other.lower, true) >= 0 ? lower : other.lower;
        var newUpper = compare(upper, false, other.upper, false) <= 0 ? upper : other.upper;
        return Optional.of(new Range<>(newLower, newUpper));
    }

    /**
     * Returns the smallest range that encloses this range and another one, with the values between them if they are not
     * connected.
     *
     * @param other the other range
     * @return the range from the lower of both lower bounds to the higher of both upper bounds
     */
    public Range<C> span(Range<C> other) {
        requireNonNull(other, "other");
        var newLower = compare(lower, true, other.lower, true) <= 0 ? lower : other.lower;
        var newUpper = compare(upper, false, other.upper, false) >= 0 ? upper : other.upper;
        return new Range<>(newLower, newUpper);
    }

    /**
     * Returns this range in interval notation, such as {@code [0, 10)} or {@code (-∞, 5]}.
     *
     * @return this range as text
     */
    @Override
    public String toString() {
        return format(lower, upper);
    }

    private static String format(Bound<?> lower, Bound<?> upper) {
        var lowerText = switch (lower) {
            case Bound.Closed<?> closed -> "[" + closed.endpoint();
            case Bound.Open<?> open -> "(" + open.endpoint();
            case Bound.Unbounded<?>() -> "(-∞";
        };
        var upperText = switch (upper) {
            case Bound.Closed<?> closed -> closed.endpoint() + "]";
            case Bound.Open<?> open -> open.endpoint() + ")";
            case Bound.Unbounded<?>() -> "+∞)";
        };
        return lowerText + ", " + upperText;
    }

    /*
     * A bound is a cut between values. A lower bound is at -∞ when unbounded, just below its endpoint when closed, and
     * just above it when open. An upper bound is at +∞ when unbounded, just above its endpoint when closed, and just
     * below it when open. Comparing two cuts compares their endpoints first, then the side they are on.
     */

    /** Compares two bounds as cuts, each being a lower bound if its flag is {@code true}. */
    private static <C extends Comparable<? super C>> int compare(
            Bound<C> first, boolean firstIsLower, Bound<C> second, boolean secondIsLower) {
        var firstInfinity = infinity(first, firstIsLower);
        var secondInfinity = infinity(second, secondIsLower);
        if (firstInfinity != 0 || secondInfinity != 0) {
            return Integer.compare(firstInfinity, secondInfinity);
        }
        var byEndpoint = endpoint(first).compareTo(endpoint(second));
        return byEndpoint != 0 ? byEndpoint : Integer.compare(side(first, firstIsLower), side(second, secondIsLower));
    }

    /** Compares a bound, as a cut, with a value. */
    private static <C extends Comparable<? super C>> int compareToValue(Bound<C> bound, boolean isLower, C value) {
        var infinity = infinity(bound, isLower);
        if (infinity != 0) {
            return infinity;
        }
        var byEndpoint = endpoint(bound).compareTo(value);
        return byEndpoint != 0 ? byEndpoint : side(bound, isLower);
    }

    /** Returns -1 for -∞, 1 for +∞, and 0 for a bound with an endpoint. */
    private static int infinity(Bound<?> bound, boolean isLower) {
        if (bound instanceof Bound.Unbounded<?>) {
            return isLower ? -1 : 1;
        }
        return 0;
    }

    /** Returns -1 for a cut just below the endpoint, and 1 for a cut just above it. */
    private static int side(Bound<?> bound, boolean isLower) {
        return (bound instanceof Bound.Closed<?>) == isLower ? -1 : 1;
    }

    private static <C> C endpoint(Bound<C> bound) {
        return switch (bound) {
            case Bound.Closed<C> closed -> closed.endpoint();
            case Bound.Open<C> open -> open.endpoint();
            case Bound.Unbounded<C>() -> throw new AssertionError("no endpoint");
        };
    }

    /**
     * A bound of a range: closed, open, or absent.
     *
     * @param <C> the type of the values
     * @since 0.1.2
     */
    public sealed interface Bound<C> extends Serializable {

        /**
         * A bound that includes its endpoint.
         *
         * @param <C> the type of the values
         * @param endpoint the endpoint
         * @since 0.1.2
         */
        record Closed<C>(C endpoint) implements Bound<C> {

            /**
             * Creates a closed bound.
             *
             * @param endpoint the endpoint
             */
            public Closed {
                requireNonNull(endpoint, "endpoint");
            }
        }

        /**
         * A bound that excludes its endpoint.
         *
         * @param <C> the type of the values
         * @param endpoint the endpoint
         * @since 0.1.2
         */
        record Open<C>(C endpoint) implements Bound<C> {

            /**
             * Creates an open bound.
             *
             * @param endpoint the endpoint
             */
            public Open {
                requireNonNull(endpoint, "endpoint");
            }
        }

        /**
         * The absence of a bound: the range extends forever on that side.
         *
         * @param <C> the type of the values
         * @since 0.1.2
         */
        record Unbounded<C>() implements Bound<C> {}
    }
}
