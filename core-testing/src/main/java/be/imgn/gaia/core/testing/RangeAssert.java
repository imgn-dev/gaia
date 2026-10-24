package be.imgn.gaia.core.testing;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.collect.Range;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

/**
 * AssertJ assertions on a {@link Range}.
 *
 * <pre>{@code
 * assertThat(Range.closedOpen(0, 10)).contains(0, 9).doesNotContain(10).encloses(Range.closed(2, 5));
 * }</pre>
 *
 * @param <C> the type of the values
 * @since 0.1.2
 */
public final class RangeAssert<C extends Comparable<? super C>> extends AbstractAssert<RangeAssert<C>, Range<C>> {

    private RangeAssert(@Nullable Range<C> actual) {
        super(actual, RangeAssert.class);
    }

    /**
     * Returns assertions on a range.
     *
     * @param <C> the type of the values
     * @param actual the range; like any AssertJ assertion, every assertion fails if it is {@code null}
     * @return the assertions
     */
    public static <C extends Comparable<? super C>> RangeAssert<C> assertThat(@Nullable Range<C> actual) {
        return new RangeAssert<>(actual);
    }

    /**
     * Verifies that the range contains all the given values.
     *
     * @param values the values
     * @return these assertions
     */
    @SafeVarargs
    public final RangeAssert<C> contains(C... values) {
        requireNonNull(values, "values");
        isNotNull();
        var missing = new ArrayList<C>();
        for (var i = 0; i < values.length; i++) {
            if (!actual.contains(requireNonNull(values[i], "values[" + i + "]"))) {
                missing.add(values[i]);
            }
        }
        if (!missing.isEmpty()) {
            failWithMessage("Expected %s to contain %s", actual, join(missing));
        }
        return this;
    }

    /**
     * Verifies that the range contains none of the given values.
     *
     * @param values the values
     * @return these assertions
     */
    @SafeVarargs
    public final RangeAssert<C> doesNotContain(C... values) {
        requireNonNull(values, "values");
        isNotNull();
        var present = new ArrayList<C>();
        for (var i = 0; i < values.length; i++) {
            if (actual.contains(requireNonNull(values[i], "values[" + i + "]"))) {
                present.add(values[i]);
            }
        }
        if (!present.isEmpty()) {
            failWithMessage("Expected %s not to contain %s", actual, join(present));
        }
        return this;
    }

    /**
     * Verifies that the range is empty, as {@code [3, 3)} is.
     *
     * @return these assertions
     */
    public RangeAssert<C> isEmpty() {
        isNotNull();
        if (!actual.isEmpty()) {
            failWithMessage("Expected %s to be empty", actual);
        }
        return this;
    }

    /**
     * Verifies that the range is not empty.
     *
     * @return these assertions
     */
    public RangeAssert<C> isNotEmpty() {
        isNotNull();
        if (actual.isEmpty()) {
            failWithMessage("Expected %s not to be empty", actual);
        }
        return this;
    }

    /**
     * Verifies that the range encloses another one.
     *
     * @param other the other range
     * @return these assertions
     * @see Range#encloses(Range)
     */
    public RangeAssert<C> encloses(Range<C> other) {
        requireNonNull(other, "other");
        isNotNull();
        if (!actual.encloses(other)) {
            failWithMessage("Expected %s to enclose %s", actual, other);
        }
        return this;
    }

    /**
     * Verifies that the range does not enclose another one.
     *
     * @param other the other range
     * @return these assertions
     * @see Range#encloses(Range)
     */
    public RangeAssert<C> doesNotEnclose(Range<C> other) {
        requireNonNull(other, "other");
        isNotNull();
        if (actual.encloses(other)) {
            failWithMessage("Expected %s not to enclose %s", actual, other);
        }
        return this;
    }

    /**
     * Verifies that the range is connected to another one: that they overlap or are adjacent.
     *
     * @param other the other range
     * @return these assertions
     * @see Range#isConnected(Range)
     */
    public RangeAssert<C> isConnectedTo(Range<C> other) {
        requireNonNull(other, "other");
        isNotNull();
        if (!actual.isConnected(other)) {
            failWithMessage("Expected %s to be connected to %s", actual, other);
        }
        return this;
    }

    /**
     * Verifies that the range is not connected to another one: that some value lies between them.
     *
     * @param other the other range
     * @return these assertions
     * @see Range#isConnected(Range)
     */
    public RangeAssert<C> isNotConnectedTo(Range<C> other) {
        requireNonNull(other, "other");
        isNotNull();
        if (actual.isConnected(other)) {
            failWithMessage("Expected %s not to be connected to %s", actual, other);
        }
        return this;
    }

    private static String join(List<?> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }
}
