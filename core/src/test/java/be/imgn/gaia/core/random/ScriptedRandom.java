package be.imgn.gaia.core.random;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Override
    public long nextLong() {
        throw new UnsupportedOperationException();
    }

    boolean isExhausted() {
        return numbers.isEmpty();
    }
}
