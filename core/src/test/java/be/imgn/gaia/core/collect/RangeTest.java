package be.imgn.gaia.core.collect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import be.imgn.gaia.core.collect.Range.Bound;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RangeTest {

    static Stream<Arguments> everyFactory() {
        return Stream.of(
                Arguments.of(Range.closed(2, 5), "[2, 5]", List.of(2, 3, 4, 5)),
                Arguments.of(Range.open(2, 5), "(2, 5)", List.of(3, 4)),
                Arguments.of(Range.closedOpen(2, 5), "[2, 5)", List.of(2, 3, 4)),
                Arguments.of(Range.openClosed(2, 5), "(2, 5]", List.of(3, 4, 5)),
                Arguments.of(Range.atLeast(5), "[5, +∞)", List.of(5, 6, 7)),
                Arguments.of(Range.greaterThan(5), "(5, +∞)", List.of(6, 7)),
                Arguments.of(Range.atMost(2), "(-∞, 2]", List.of(0, 1, 2)),
                Arguments.of(Range.lessThan(2), "(-∞, 2)", List.of(0, 1)),
                Arguments.of(Range.<Integer>all(), "(-∞, +∞)", List.of(0, 1, 2, 3, 4, 5, 6, 7)),
                Arguments.of(Range.closed(3, 3), "[3, 3]", List.of(3)),
                Arguments.of(Range.closedOpen(3, 3), "[3, 3)", List.of()),
                Arguments.of(Range.openClosed(3, 3), "(3, 3]", List.of()));
    }

    @ParameterizedTest
    @MethodSource("everyFactory")
    void containsTheValuesBetweenItsBounds(Range<Integer> range, String text, List<Integer> values) {
        assertThat(IntStream.rangeClosed(0, 7).boxed().filter(range::contains)).containsExactlyElementsOf(values);
        assertThat(range).hasToString(text);
        assertThat(range.isEmpty()).isEqualTo(values.isEmpty());
    }

    @Test
    void exposesItsBoundsForPatternMatching() {
        Range<Integer> range = Range.closedOpen(2, 5);

        assertThat(range.lower()).isEqualTo(new Bound.Closed<>(2));
        assertThat(range.upper()).isEqualTo(new Bound.Open<>(5));
        assertThat(Range.atLeast(2).upper()).isEqualTo(new Bound.Unbounded<>());
        assertThat(new Range<>(new Bound.Closed<>(2), new Bound.Open<>(5))).isEqualTo(range);
        var lowest = switch (range.lower()) {
            case Bound.Closed<Integer>(var endpoint) -> endpoint;
            case Bound.Open<Integer>(var endpoint) -> endpoint + 1;
            case Bound.Unbounded<Integer>() -> Integer.MIN_VALUE;
        };
        assertThat(lowest).isEqualTo(2);
    }

    @Test
    void comparesValuesWithCompareTo() {
        assertThat(Range.closed(new BigDecimal("1.0"), new BigDecimal("2.0")).contains(new BigDecimal("2.00")))
                .isTrue();
    }

    @Test
    void rejectsALowerBoundAboveTheUpperBound() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Range.closed(5, 2))
                .withMessage("invalid range [5, 2]: the lower bound is above the upper bound");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Range.open(3, 3))
                .withMessage("invalid range (3, 3): the lower bound is above the upper bound");
        assertThatIllegalArgumentException().isThrownBy(() -> Range.closedOpen(5, 2));
    }

    // Passing null is the point.
    @Test
    @SuppressWarnings("NullAway")
    void rejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> Range.closed(null, 2)).withMessage("endpoint");
        assertThatNullPointerException().isThrownBy(() -> Range.open(1, null)).withMessage("endpoint");
        assertThatNullPointerException()
                .isThrownBy(() -> new Range<>(null, new Bound.Unbounded<Integer>()))
                .withMessage("lower");
        assertThatNullPointerException()
                .isThrownBy(() -> new Range<>(new Bound.Unbounded<Integer>(), null))
                .withMessage("upper");
        var range = Range.closed(1, 2);
        assertThatNullPointerException().isThrownBy(() -> range.contains(null)).withMessage("value");
        assertThatNullPointerException().isThrownBy(() -> range.encloses(null)).withMessage("other");
        assertThatNullPointerException()
                .isThrownBy(() -> range.isConnected(null))
                .withMessage("other");
        assertThatNullPointerException()
                .isThrownBy(() -> range.intersection(null))
                .withMessage("other");
        assertThatNullPointerException().isThrownBy(() -> range.span(null)).withMessage("other");
    }

    @Test
    void enclosesTheRangesWithinItsBounds() {
        Range<Integer> range = Range.closedOpen(2, 5);

        assertThat(range.encloses(Range.closed(2, 4))).isTrue();
        assertThat(range.encloses(Range.open(2, 5))).isTrue();
        assertThat(range.encloses(range)).isTrue();
        assertThat(range.encloses(Range.closedOpen(5, 5))).isTrue();
        assertThat(range.encloses(Range.closed(2, 5))).isFalse();
        assertThat(range.encloses(Range.atLeast(3))).isFalse();
        assertThat(Range.<Integer>all().encloses(Range.atMost(3))).isTrue();
        assertThat(Range.atMost(3).encloses(Range.<Integer>all())).isFalse();
    }

    @Test
    void isConnectedToTheRangesItOverlapsOrTouches() {
        Range<Integer> range = Range.closedOpen(2, 5);

        assertThat(range.isConnected(Range.closed(4, 8))).isTrue();
        assertThat(range.isConnected(Range.closed(5, 8))).isTrue();
        assertThat(range.isConnected(Range.open(5, 8))).isFalse();
        assertThat(range.isConnected(Range.atMost(2))).isTrue();
        assertThat(range.isConnected(Range.lessThan(2))).isTrue();
        assertThat(Range.open(2, 5).isConnected(Range.lessThan(2))).isFalse();
        assertThat(range.isConnected(Range.closed(7, 8))).isFalse();
    }

    @Test
    void intersectsConnectedRanges() {
        Range<Integer> range = Range.closedOpen(2, 5);

        assertThat(range.intersection(Range.closed(4, 8))).contains(Range.closedOpen(4, 5));
        assertThat(range.intersection(Range.atLeast(1))).contains(range);
        assertThat(range.intersection(Range.open(2, 3))).contains(Range.open(2, 3));
        assertThat(range.intersection(Range.closed(5, 8))).contains(Range.closedOpen(5, 5));
        assertThat(range.intersection(Range.open(5, 8))).isEmpty();
    }

    @Test
    void spansBothRangesAndTheValuesBetweenThem() {
        assertThat(Range.closedOpen(2, 5).span(Range.open(7, 8))).isEqualTo(Range.closedOpen(2, 8));
        assertThat(Range.closedOpen(2, 5).span(Range.closed(3, 5))).isEqualTo(Range.closed(2, 5));
        assertThat(Range.open(2, 5).span(Range.atMost(2))).isEqualTo(Range.lessThan(5));
        assertThat(Range.closed(2, 5).span(Range.closed(2, 5))).isEqualTo(Range.closed(2, 5));
    }

    @Test
    void distinguishesEmptyRangesByTheirBounds() {
        assertThat(Range.closedOpen(3, 3)).isNotEqualTo(Range.openClosed(3, 3));
        assertThat(Range.closedOpen(3, 3)).isEqualTo(Range.closedOpen(3, 3)).hasSameHashCodeAs(Range.closedOpen(3, 3));
    }

    @Test
    void survivesSerialization() throws IOException, ClassNotFoundException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(Range.closedOpen(2, 5));
            out.writeObject(Range.atLeast(2));
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(in.readObject()).isEqualTo(Range.closedOpen(2, 5));
            assertThat(in.readObject()).isEqualTo(Range.atLeast(2));
        }
    }
}
