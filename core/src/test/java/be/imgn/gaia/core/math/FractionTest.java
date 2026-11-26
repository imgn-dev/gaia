package be.imgn.gaia.core.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class FractionTest {

    @Test
    void fractionsOfAnyClassCompareByValue() {
        Fraction third = IntFraction.of(1, 3);
        Fraction half = LongFraction.of(1, 2);
        Fraction large = BigFraction.of(2, 3).pow(-40);

        assertThat(third.compareTo(half)).isNegative();
        assertThat(half.compareTo(third)).isPositive();
        assertThat(half.compareTo(large)).isNegative();
        assertThat(large.compareTo(third)).isPositive();
        assertThat(half.compareTo(IntFraction.of(2, 4))).isZero();
        assertThat(BigFraction.of(1, 2).compareTo(half)).isZero();
        assertThat(LongFraction.of(Long.MAX_VALUE).compareTo(IntFraction.of(Integer.MAX_VALUE)))
                .isPositive();
    }

    @Test
    void fractionsOfDifferentClassesAreNotEqual() {
        assertThat(IntFraction.of(1, 2)).isNotEqualTo(LongFraction.of(1, 2)).isNotEqualTo(BigFraction.of(1, 2));
        assertThat(LongFraction.of(1, 2)).isNotEqualTo(BigFraction.of(1, 2));
    }

    @Test
    void fractionsOfAnyClassSortTogether() {
        var fractions = new ArrayList<Fraction>(
                List.of(BigFraction.of(3, 4), IntFraction.of(-1, 2), LongFraction.of(1, 3), IntFraction.ZERO));

        Collections.sort(fractions);

        assertThat(fractions)
                .containsExactly(IntFraction.of(-1, 2), IntFraction.ZERO, LongFraction.of(1, 3), BigFraction.of(3, 4));
    }

    @Test
    void anyFractionConvertsToAnyClass() {
        for (Fraction fraction : List.of(IntFraction.of(-3, 4), LongFraction.of(-3, 4), BigFraction.of(-3, 4))) {
            assertThat(fraction.toIntFractionExact()).isEqualTo(IntFraction.of(-3, 4));
            assertThat(fraction.toLongFractionExact()).isEqualTo(LongFraction.of(-3, 4));
            assertThat(fraction.toBigFraction()).isEqualTo(BigFraction.of(-3, 4));
            assertThat(fraction.signum()).isEqualTo(-1);
            assertThat(fraction.isInteger()).isFalse();
            assertThat(fraction.doubleValue()).isEqualTo(-0.75);
            assertThat(fraction).hasToString("-3/4");
        }
    }

    @Test
    void conversionsToTheSameClassReturnTheFraction() {
        var small = IntFraction.of(1, 2);
        var medium = LongFraction.of(1, 2);
        var big = BigFraction.of(1, 2);

        assertThat(small.toIntFractionExact()).isSameAs(small);
        assertThat(medium.toLongFractionExact()).isSameAs(medium);
        assertThat(big.toBigFraction()).isSameAs(big);
    }

    @Test
    void narrowingFailsWhenTheFractionDoesNotFit() {
        Fraction large = LongFraction.of(1L << 40, 3);

        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(large::toIntFractionExact);
    }

    @Test
    void aSwitchCoversTheThreeClasses() {
        Fraction fraction = IntFraction.of(1, 2);

        String size = switch (fraction) {
            case IntFraction small -> "int";
            case LongFraction medium -> "long";
            case BigFraction big -> "big";
        };

        assertThat(size).isEqualTo("int");
    }
}
