package be.imgn.gaia.core.random;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.random.RandomGenerator;

/** A source of randomness that gives the numbers it was given, in order: whole ones and fractions alike. */
final class ScriptedRandom implements RandomGenerator {

    private final Deque<Double> numbers = new ArrayDeque<>();

    ScriptedRandom(double... numbers) {
        for (double number : numbers) {
            this.numbers.add(number);
        }
    }

    @Override
    public int nextInt(int bound) {
        int number = (int) (double) numbers.remove();
        assertThat(number).isBetween(0, bound - 1);
        return number;
    }

    @Override
    public int nextInt(int origin, int bound) {
        int number = (int) (double) numbers.remove();
        assertThat(number).isBetween(origin, bound - 1);
        return number;
    }

    @Override
    public double nextDouble() {
        return numbers.remove();
    }

    /** Gives the next number, a fraction, as the same fraction of all the {@code long} values. */
    @Override
    public long nextLong() {
        return new BigDecimal(numbers.remove())
                .multiply(new BigDecimal(BigInteger.ONE.shiftLeft(Long.SIZE)))
                .toBigInteger()
                .longValue();
    }

    boolean isExhausted() {
        return numbers.isEmpty();
    }
}
