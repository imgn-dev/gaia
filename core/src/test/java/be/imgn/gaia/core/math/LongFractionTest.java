package be.imgn.gaia.core.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigInteger;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class LongFractionTest {

    @Test
    void ofReducesToLowestTermsWithAPositiveDenominator() {
        var fraction = LongFraction.of(6, -8);

        assertThat(fraction.numerator()).isEqualTo(-3);
        assertThat(fraction.denominator()).isEqualTo(4);
        assertThat(LongFraction.of(0, -7)).isSameAs(LongFraction.ZERO);
        assertThat(LongFraction.of(5, 5)).isSameAs(LongFraction.ONE);
        assertThat(LongFraction.of(1L << 40, 1L << 42)).isEqualTo(LongFraction.of(1, 4));
    }

    @Test
    void ofHandlesTheLeastLong() {
        assertThat(LongFraction.of(Long.MIN_VALUE, Long.MIN_VALUE)).isSameAs(LongFraction.ONE);
        assertThat(LongFraction.of(Long.MIN_VALUE, 2)).isEqualTo(LongFraction.of(Long.MIN_VALUE / 2));
        assertThat(LongFraction.of(Long.MIN_VALUE, 1).numerator()).isEqualTo(Long.MIN_VALUE);
        // The denominator would be 2^63.
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> LongFraction.of(1, Long.MIN_VALUE));
    }

    @Test
    void ofRejectsWhatDoesNotFit() {
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> LongFraction.of(1, 0));
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> LongFraction.of(BigInteger.ONE, BigInteger.TWO.pow(63)));
        assertThat(LongFraction.of(BigInteger.TEN.pow(30), BigInteger.TEN.pow(31)))
                .isEqualTo(LongFraction.of(1, 10));
    }

    @Test
    void arithmetic() {
        var half = LongFraction.of(1, 2);
        var third = LongFraction.of(1, 3);

        assertThat(half.plus(third)).isEqualTo(LongFraction.of(5, 6));
        assertThat(half.minus(third)).isEqualTo(LongFraction.of(1, 6));
        assertThat(half.times(third)).isEqualTo(LongFraction.of(1, 6));
        assertThat(half.dividedBy(third)).isEqualTo(LongFraction.of(3, 2));
        assertThat(half.plus(half)).isSameAs(LongFraction.ONE);
        assertThat(half.negate()).isEqualTo(LongFraction.of(-1, 2));
        assertThat(half.negate().abs()).isEqualTo(half);
        assertThat(LongFraction.of(-2, 3).reciprocal()).isEqualTo(LongFraction.of(-3, 2));
    }

    @Test
    void arithmeticUsesWideIntermediateResults() {
        var large = LongFraction.of(Long.MAX_VALUE, 2);

        assertThat(large.minus(large)).isSameAs(LongFraction.ZERO);
        assertThat(large.times(large.reciprocal())).isSameAs(LongFraction.ONE);
        assertThat(large.dividedBy(large)).isSameAs(LongFraction.ONE);
        assertThat(large.plus(large)).isEqualTo(LongFraction.of(Long.MAX_VALUE));
    }

    @Test
    void arithmeticRejectsWhatDoesNotFit() {
        var large = LongFraction.of(Long.MAX_VALUE);
        var least = LongFraction.of(Long.MIN_VALUE);

        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> large.plus(LongFraction.ONE));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> large.times(large));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(least::negate);
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(least::abs);
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(least::reciprocal);
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> LongFraction.ONE.dividedBy(LongFraction.ZERO));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(LongFraction.ZERO::reciprocal);
    }

    @Test
    void comparesByValueEvenWhenTheProductsOverflow() {
        assertThat(LongFraction.of(1, 3)).isLessThan(LongFraction.of(1, 2));
        assertThat(LongFraction.of(-1, 2)).isLessThan(LongFraction.of(-1, 3));
        assertThat(LongFraction.of(Long.MAX_VALUE, Long.MAX_VALUE - 1))
                .isLessThan(LongFraction.of(Long.MAX_VALUE - 1, Long.MAX_VALUE - 2));
        assertThat(LongFraction.of(Long.MIN_VALUE)).isLessThan(LongFraction.of(Long.MAX_VALUE));
        assertThat(LongFraction.of(Long.MIN_VALUE, 3)).isLessThan(LongFraction.of(Long.MIN_VALUE, 5));
    }

    @Test
    void comparesLikeBigFractions() {
        var random = new SplittableRandom(1);

        for (int i = 0; i < 10_000; i++) {
            var left = LongFraction.of(random.nextLong(), random.nextLong(1, Long.MAX_VALUE));
            var right = LongFraction.of(random.nextLong(), random.nextLong(1, Long.MAX_VALUE));
            assertThat(Integer.signum(left.compareTo(right)))
                    .isEqualTo(Integer.signum(left.toBigFraction().compareTo(right.toBigFraction())));
        }
    }

    @Test
    void widensAndNarrows() {
        assertThat(IntFraction.of(3, 4).toLongFraction()).isEqualTo(LongFraction.of(3, 4));
        assertThat(IntFraction.of(3, 4).toBigFraction()).isEqualTo(BigFraction.of(3, 4));
        assertThat(LongFraction.of(3, 4).toIntFractionExact()).isEqualTo(IntFraction.of(3, 4));
        assertThat(LongFraction.of(3, 4).toBigFraction()).isEqualTo(BigFraction.of(3, 4));
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> LongFraction.of(1L << 40, 3).toIntFractionExact());
    }

    @Test
    void conversions() {
        assertThat(LongFraction.of(7, 2).intValue()).isEqualTo(3);
        assertThat(LongFraction.of(-7, 2).longValue()).isEqualTo(-3L);
        assertThat(LongFraction.of(1, 4).doubleValue()).isEqualTo(0.25);
        assertThat(LongFraction.of(1, 3).doubleValue()).isEqualTo(1.0 / 3.0);
        assertThat(LongFraction.of(Long.MAX_VALUE, Long.MAX_VALUE - 1).doubleValue())
                .isEqualTo(1.0);
        assertThat(LongFraction.of(1, 4).floatValue()).isEqualTo(0.25f);
        assertThat(LongFraction.of(-1, 3).signum()).isEqualTo(-1);
        assertThat(LongFraction.of(4, 2).isInteger()).isTrue();
    }

    @Test
    void equalFractionsHaveEqualHashCodes() {
        assertThat(LongFraction.of(2, 4)).isEqualTo(LongFraction.of(1, 2)).hasSameHashCodeAs(LongFraction.of(1, 2));
        assertThat(LongFraction.of(1, 2)).isNotEqualTo(LongFraction.of(2, 1));
    }

    @Test
    void toStringOmitsADenominatorOfOne() {
        assertThat(LongFraction.of(11, 20)).hasToString("11/20");
        assertThat(LongFraction.of(-6, 3)).hasToString("-2");
    }
}
