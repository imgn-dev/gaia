package be.imgn.gaia.core.math;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class IntFractionTest {

    @Test
    void ofReducesToLowestTerms() {
        var fraction = IntFraction.of(6, 8);

        assertThat(fraction.numerator()).isEqualTo(3);
        assertThat(fraction.denominator()).isEqualTo(4);
    }

    @Test
    void ofMovesTheSignToTheNumerator() {
        assertThat(IntFraction.of(1, -2)).isEqualTo(IntFraction.of(-1, 2));
        assertThat(IntFraction.of(-1, -2)).isEqualTo(IntFraction.of(1, 2));
        assertThat(IntFraction.of(1, -2).denominator()).isEqualTo(2);
    }

    @Test
    void zeroHasOneRepresentation() {
        assertThat(IntFraction.of(0, -7)).isEqualTo(IntFraction.ZERO);
        assertThat(IntFraction.of(0, -7).denominator()).isEqualTo(1);
        assertThat(IntFraction.of(0)).isSameAs(IntFraction.ZERO);
        assertThat(IntFraction.of(1)).isSameAs(IntFraction.ONE);
    }

    @Test
    void ofRejectsAZeroDenominator() {
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> IntFraction.of(1, 0));
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> IntFraction.of(BigInteger.ONE, BigInteger.ZERO));
    }

    @Test
    void ofAcceptsWhatFitsOnceReduced() {
        assertThat(IntFraction.of(6_000_000_000L, 4_000_000_000L)).isEqualTo(IntFraction.of(3, 2));
        assertThat(IntFraction.of(Long.MIN_VALUE, Long.MIN_VALUE)).isEqualTo(IntFraction.ONE);
        assertThat(IntFraction.of(Long.MIN_VALUE, 1L << 62)).isEqualTo(IntFraction.of(-2));
        assertThat(IntFraction.of(BigInteger.TEN.pow(30), BigInteger.TEN.pow(31)))
                .isEqualTo(IntFraction.of(1, 10));
        assertThat(IntFraction.of(Integer.MIN_VALUE, 1).numerator()).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void ofRejectsWhatDoesNotFit() {
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> IntFraction.of(1L << 31, 1));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> IntFraction.of(1, 1L << 31));
        // The denominator would be 2^31.
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> IntFraction.of(1, Integer.MIN_VALUE));
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> IntFraction.of(BigInteger.ONE, BigInteger.TEN.pow(10)));
    }

    @Test
    void arithmetic() {
        var half = IntFraction.of(1, 2);
        var third = IntFraction.of(1, 3);

        assertThat(half.plus(third)).isEqualTo(IntFraction.of(5, 6));
        assertThat(half.minus(third)).isEqualTo(IntFraction.of(1, 6));
        assertThat(third.minus(half)).isEqualTo(IntFraction.of(-1, 6));
        assertThat(half.times(third)).isEqualTo(IntFraction.of(1, 6));
        assertThat(half.dividedBy(third)).isEqualTo(IntFraction.of(3, 2));
        assertThat(half.plus(half)).isSameAs(IntFraction.ONE);
        assertThat(half.negate()).isEqualTo(IntFraction.of(-1, 2));
        assertThat(half.negate().abs()).isEqualTo(half);
        assertThat(IntFraction.of(-2, 3).reciprocal()).isEqualTo(IntFraction.of(-3, 2));
    }

    @Test
    void arithmeticUsesWideIntermediateResults() {
        var large = IntFraction.of(Integer.MAX_VALUE, 2);

        assertThat(large.minus(large)).isEqualTo(IntFraction.ZERO);
        assertThat(large.times(large.reciprocal())).isEqualTo(IntFraction.ONE);
        assertThat(large.dividedBy(large)).isEqualTo(IntFraction.ONE);
    }

    @Test
    void arithmeticRejectsWhatDoesNotFit() {
        var large = IntFraction.of(Integer.MAX_VALUE);
        var least = IntFraction.of(Integer.MIN_VALUE);

        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> large.plus(IntFraction.ONE));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(() -> large.times(large));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(least::negate);
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(least::abs);
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(least::reciprocal);
    }

    @Test
    void divisionByZeroIsRejected() {
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> IntFraction.ONE.dividedBy(IntFraction.ZERO));
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(IntFraction.ZERO::reciprocal);
    }

    @Test
    void comparesByValue() {
        assertThat(IntFraction.of(1, 3)).isLessThan(IntFraction.of(1, 2));
        assertThat(IntFraction.of(-1, 2)).isLessThan(IntFraction.of(-1, 3));
        assertThat(IntFraction.of(2, 4)).isEqualByComparingTo(IntFraction.of(1, 2));
        assertThat(IntFraction.of(Integer.MAX_VALUE, Integer.MAX_VALUE - 1))
                .isLessThan(IntFraction.of(Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 2));
        assertThat(IntFraction.of(Integer.MIN_VALUE)).isLessThan(IntFraction.of(Integer.MAX_VALUE));
    }

    @Test
    void equalFractionsHaveEqualHashCodes() {
        assertThat(IntFraction.of(2, 4)).hasSameHashCodeAs(IntFraction.of(1, 2));
        assertThat(IntFraction.of(1, 2)).isNotEqualTo(IntFraction.of(2, 1));
    }

    @Test
    void signumAndIsInteger() {
        assertThat(IntFraction.of(-1, 2).signum()).isEqualTo(-1);
        assertThat(IntFraction.ZERO.signum()).isZero();
        assertThat(IntFraction.of(1, 2).signum()).isEqualTo(1);
        assertThat(IntFraction.of(4, 2).isInteger()).isTrue();
        assertThat(IntFraction.of(1, 2).isInteger()).isFalse();
    }

    @Test
    void conversionsRoundTowardZero() {
        assertThat(IntFraction.of(7, 2).intValue()).isEqualTo(3);
        assertThat(IntFraction.of(-7, 2).intValue()).isEqualTo(-3);
        assertThat(IntFraction.of(-7, 2).longValue()).isEqualTo(-3L);
        assertThat(IntFraction.of(1, 4).doubleValue()).isEqualTo(0.25);
        assertThat(IntFraction.of(1, 3).doubleValue()).isEqualTo(1.0 / 3.0);
        assertThat(IntFraction.of(1, 4).floatValue()).isEqualTo(0.25f);
    }

    @Test
    void toStringOmitsADenominatorOfOne() {
        assertThat(IntFraction.of(11, 20)).hasToString("11/20");
        assertThat(IntFraction.of(-1, 2)).hasToString("-1/2");
        assertThat(IntFraction.of(6, 3)).hasToString("2");
    }

    @Test
    @SuppressWarnings("NullAway")
    void nullsAreRejected() {
        assertThatNullPointerException().isThrownBy(() -> IntFraction.of(null, BigInteger.ONE));
        assertThatNullPointerException().isThrownBy(() -> IntFraction.of(BigInteger.ONE, null));
        assertThatNullPointerException().isThrownBy(() -> IntFraction.ONE.plus(null));
    }
}
