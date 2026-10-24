package be.imgn.gaia.core.tests;

import static be.imgn.gaia.core.testing.RangeAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.collect.Range;
import org.junit.jupiter.api.Test;

class RangeAssertTest {

    private static final Range<Integer> DIGITS = Range.closedOpen(0, 10);

    @Test
    void passesWhenTheRangeMatches() {
        assertThat(DIGITS)
                .contains(0, 5, 9)
                .doesNotContain(-1, 10)
                .isNotEmpty()
                .encloses(Range.closed(2, 5))
                .doesNotEnclose(Range.closed(5, 10))
                .isConnectedTo(Range.atLeast(10))
                .isNotConnectedTo(Range.greaterThan(10))
                .isEqualTo(Range.closedOpen(0, 10));
        assertThat(Range.closedOpen(3, 3)).isEmpty();
    }

    @Test
    void reportsTheValuesThatFail() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(DIGITS).contains(0, 10, 11))
                .withMessage("Expected [0, 10) to contain 10, 11");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(DIGITS).doesNotContain(-1, 0))
                .withMessage("Expected [0, 10) not to contain 0");
    }

    @Test
    void reportsTheRangeThatFails() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(DIGITS).isEmpty())
                .withMessage("Expected [0, 10) to be empty");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(Range.closedOpen(3, 3)).isNotEmpty())
                .withMessage("Expected [3, 3) not to be empty");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(DIGITS).encloses(Range.closed(5, 10)))
                .withMessage("Expected [0, 10) to enclose [5, 10]");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(DIGITS).doesNotEnclose(Range.closed(2, 5)))
                .withMessage("Expected [0, 10) not to enclose [2, 5]");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(DIGITS).isConnectedTo(Range.greaterThan(10)))
                .withMessage("Expected [0, 10) to be connected to (10, +∞)");
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat(DIGITS).isNotConnectedTo(Range.atLeast(10)))
                .withMessage("Expected [0, 10) not to be connected to [10, +∞)");
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void failsOnANullRangeAndRejectsNullArguments() {
        assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> assertThat((Range<Integer>) null).contains(1));
        assertThatNullPointerException()
                .isThrownBy(() -> assertThat(DIGITS).contains(1, null))
                .withMessage("values[1]");
        assertThatNullPointerException()
                .isThrownBy(() -> assertThat(DIGITS).encloses(null))
                .withMessage("other");
    }
}
