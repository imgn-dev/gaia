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
    void operationsReturnTheLargerClassOfTheTwo() {
        Fraction small = IntFraction.of(1, 2);
        Fraction medium = LongFraction.of(1, 3);
        Fraction big = BigFraction.of(1, 6);

        assertThat(small.plus(small)).isEqualTo(IntFraction.ONE);
        assertThat(small.plus(medium)).isEqualTo(LongFraction.of(5, 6));
        assertThat(medium.plus(small)).isEqualTo(LongFraction.of(5, 6));
        assertThat(medium.minus(big)).isEqualTo(BigFraction.of(1, 6));
        assertThat(small.times(big)).isEqualTo(BigFraction.of(1, 12));
        assertThat(small.dividedBy(medium)).isEqualTo(LongFraction.of(3, 2));
        assertThat(small.minus(small)).isSameAs(IntFraction.ZERO);
    }

    @Test
    void operationsOnAClassReturnThatClass() {
        IntFraction small = IntFraction.of(1, 2).plus(IntFraction.of(1, 3));
        LongFraction medium = LongFraction.of(1, 2).times(LongFraction.of(1, 3));
        BigFraction big = BigFraction.of(1, 2).dividedBy(BigFraction.of(1, 3));
        Fraction negated = ((Fraction) small).negate();

        assertThat(small).isEqualTo(IntFraction.of(5, 6));
        assertThat(medium).isEqualTo(LongFraction.of(1, 6));
        assertThat(big).isEqualTo(BigFraction.of(3, 2));
        assertThat(negated).isEqualTo(IntFraction.of(-5, 6));
        assertThat(((Fraction) medium).reciprocal()).isEqualTo(LongFraction.of(6));
        assertThat(((Fraction) big.negate()).abs()).isEqualTo(big);
    }

    @Test
    void aResultIsNotGivenALargerClass() {
        Fraction large = IntFraction.of(Integer.MAX_VALUE);

        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> large.plus(IntFraction.ONE));
        assertThat(large.plus(LongFraction.ONE)).isEqualTo(LongFraction.of(1L << 31));
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> IntFraction.ONE.dividedBy((Fraction) LongFraction.ZERO));
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
