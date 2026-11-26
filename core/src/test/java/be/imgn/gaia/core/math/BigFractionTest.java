package be.imgn.gaia.core.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class BigFractionTest {

    @Test
    void ofReducesToLowestTermsWithAPositiveDenominator() {
        var fraction = BigFraction.of(6, -8);

        assertThat(fraction.numerator()).isEqualTo(BigInteger.valueOf(-3));
        assertThat(fraction.denominator()).isEqualTo(BigInteger.valueOf(4));
        assertThat(BigFraction.of(0, -7)).isSameAs(BigFraction.ZERO);
        assertThat(BigFraction.of(BigInteger.ZERO)).isSameAs(BigFraction.ZERO);
        assertThat(BigFraction.of(5, 5)).isEqualTo(BigFraction.ONE);
    }

    @Test
    void ofRejectsAZeroDenominator() {
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> BigFraction.of(1, 0));
    }

    @Test
    void ofADoubleIsExact() {
        assertThat(BigFraction.of(0.75)).isEqualTo(BigFraction.of(3, 4));
        assertThat(BigFraction.of(-2.5)).isEqualTo(BigFraction.of(-5, 2));
        assertThat(BigFraction.of(1e20)).isEqualTo(BigFraction.of(BigInteger.TEN.pow(20)));
        assertThat(BigFraction.of(-0.0)).isSameAs(BigFraction.ZERO);
        assertThat(BigFraction.of(0.1))
                .isEqualTo(BigFraction.of(3_602_879_701_896_397L, 36_028_797_018_963_968L))
                .isNotEqualTo(BigFraction.of(1, 10));
        assertThat(BigFraction.of(Double.MIN_VALUE).denominator()).isEqualTo(BigInteger.TWO.pow(1074));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> BigFraction.of(Double.NaN));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> BigFraction.of(Double.POSITIVE_INFINITY));
    }

    @Test
    void arithmetic() {
        var half = BigFraction.of(1, 2);
        var third = BigFraction.of(1, 3);

        assertThat(half.plus(third)).isEqualTo(BigFraction.of(5, 6));
        assertThat(half.minus(third)).isEqualTo(BigFraction.of(1, 6));
        assertThat(half.times(third)).isEqualTo(BigFraction.of(1, 6));
        assertThat(half.dividedBy(third)).isEqualTo(BigFraction.of(3, 2));
        assertThat(half.minus(half)).isSameAs(BigFraction.ZERO);
        assertThat(half.negate()).isEqualTo(BigFraction.of(-1, 2));
        assertThat(half.negate().abs()).isEqualTo(half);
        assertThat(BigFraction.of(-2, 3).reciprocal()).isEqualTo(BigFraction.of(-3, 2));
        assertThat(BigFraction.ZERO.negate()).isSameAs(BigFraction.ZERO);
    }

    @Test
    void arithmeticNeverOverflows() {
        var large = BigFraction.of(Long.MAX_VALUE, 3);

        assertThat(large.times(large).numerator())
                .isEqualTo(BigInteger.valueOf(Long.MAX_VALUE).pow(2));
        assertThat(large.plus(large))
                .isEqualTo(BigFraction.of(BigInteger.valueOf(Long.MAX_VALUE).shiftLeft(1), BigInteger.valueOf(3)));
    }

    @Test
    void pow() {
        var third = BigFraction.of(-1, 3);

        assertThat(third.pow(0)).isEqualTo(BigFraction.ONE);
        assertThat(third.pow(3)).isEqualTo(BigFraction.of(-1, 27));
        assertThat(third.pow(-2)).isEqualTo(BigFraction.of(9));
        assertThat(BigFraction.of(1, 20).pow(15).denominator())
                .isEqualTo(BigInteger.valueOf(20).pow(15));
        assertThat(BigFraction.ZERO.pow(5)).isSameAs(BigFraction.ZERO);
        assertThat(BigFraction.ZERO.pow(0)).isEqualTo(BigFraction.ONE);
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> BigFraction.ZERO.pow(-1));
    }

    @Test
    void divisionByZeroIsRejected() {
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> BigFraction.ONE.dividedBy(BigFraction.ZERO));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(BigFraction.ZERO::reciprocal);
    }

    @Test
    void comparesByValue() {
        assertThat(BigFraction.of(1, 3)).isLessThan(BigFraction.of(1, 2));
        assertThat(BigFraction.of(-1, 2)).isLessThan(BigFraction.of(-1, 3));
        assertThat(BigFraction.of(2, 4))
                .isEqualByComparingTo(BigFraction.of(1, 2))
                .hasSameHashCodeAs(BigFraction.of(1, 2));
        assertThat(BigFraction.of(1, 2)).isNotEqualTo(BigFraction.of(2, 1));
    }

    @Test
    void narrowsWhenItFits() {
        assertThat(BigFraction.of(3, 4).toIntFractionExact()).isEqualTo(IntFraction.of(3, 4));
        assertThat(BigFraction.of(3, 4).toLongFractionExact()).isEqualTo(LongFraction.of(3, 4));
        assertThat(BigFraction.of(1L << 40, 3).toLongFractionExact()).isEqualTo(LongFraction.of(1L << 40, 3));
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> BigFraction.of(1L << 40, 3).toIntFractionExact());
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> BigFraction.of(1, 3).pow(50).toLongFractionExact());
    }

    @Test
    void conversions() {
        assertThat(BigFraction.of(7, 2).intValue()).isEqualTo(3);
        assertThat(BigFraction.of(-7, 2).longValue()).isEqualTo(-3L);
        assertThat(BigFraction.of(1, 4).doubleValue()).isEqualTo(0.25);
        assertThat(BigFraction.of(1, 3).doubleValue()).isEqualTo(1.0 / 3.0);
        assertThat(BigFraction.of(0.1).doubleValue()).isEqualTo(0.1);
        assertThat(BigFraction.of(1, 3).pow(100).doubleValue()).isCloseTo(Math.pow(3, -100), within(1e-60));
        assertThat(BigFraction.of(1, 4).floatValue()).isEqualTo(0.25f);
        assertThat(BigFraction.of(1, 3).signum()).isEqualTo(1);
        assertThat(BigFraction.of(4, 2).isInteger()).isTrue();
    }

    @Test
    void toStringOmitsADenominatorOfOne() {
        assertThat(BigFraction.of(11, 20)).hasToString("11/20");
        assertThat(BigFraction.of(-6, 3)).hasToString("-2");
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullsAreRejected() {
        assertThatNullPointerException().isThrownBy(() -> BigFraction.of(null, BigInteger.ONE));
        assertThatNullPointerException().isThrownBy(() -> BigFraction.of(BigInteger.ONE, null));
        assertThatNullPointerException().isThrownBy(() -> BigFraction.ONE.plus(null));
    }
}
