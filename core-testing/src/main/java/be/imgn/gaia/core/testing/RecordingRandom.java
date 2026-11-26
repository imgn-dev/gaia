package be.imgn.gaia.core.testing;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * A {@link RandomGenerator} that gives the numbers of another one and remembers them, to play them again with a
 * {@link FakeRandom}: a behavior that came up once with real randomness becomes a test that always has it.
 *
 * <pre>{@code
 * var recording = new RecordingRandom(new SplittableRandom());
 * var rolled = expression.roll(recording);      // something odd happens
 * System.out.println(recording);                // new FakeRandom().add(5, 0, 3, 3)
 *
 * var replayed = expression.roll(recording.replay());
 * assertThat(replayed).isEqualTo(rolled);
 * }</pre>
 *
 * <p>It records the calls that {@link FakeRandom} scripts exact values for: {@code nextInt} and {@code nextLong}, with
 * or without bounds, {@code nextDouble()} and {@code nextBoolean()}. The other methods of {@link RandomGenerator} are
 * built on these ones, exactly as those of a {@link FakeRandom} are, so that they are played again too; the numbers
 * they give may differ from those that the wrapped generator would have given itself.
 *
 * <p>It isn't thread-safe.
 *
 * @since 0.2.0
 */
public final class RecordingRandom implements RandomGenerator {

    private final RandomGenerator random;
    private final List<Object> recorded = new ArrayList<>();

    /**
     * Creates a source of randomness that records the numbers of another one.
     *
     * @param random the source of randomness to take the numbers from
     */
    public RecordingRandom(RandomGenerator random) {
        this.random = requireNonNull(random, "random");
    }

    /**
     * Returns a source of randomness that gives the numbers recorded so far, in order, to the same calls.
     *
     * @return a new {@link FakeRandom} scripted with the recorded numbers
     */
    public FakeRandom replay() {
        var fake = new FakeRandom();
        for (var value : recorded) {
            if (value instanceof Integer number) {
                fake.add(number.intValue());
            } else if (value instanceof Long number) {
                fake.add(number.longValue());
            } else if (value instanceof Double number) {
                fake.add(number.doubleValue());
            } else {
                fake.add((boolean) value);
            }
        }
        return fake;
    }

    /**
     * Returns how many numbers were recorded.
     *
     * @return the number of recorded numbers
     */
    public int size() {
        return recorded.size();
    }

    @Override
    public int nextInt() {
        return record(random.nextInt());
    }

    @Override
    public int nextInt(int bound) {
        return record(random.nextInt(bound));
    }

    @Override
    public int nextInt(int origin, int bound) {
        return record(random.nextInt(origin, bound));
    }

    @Override
    public long nextLong() {
        return record(random.nextLong());
    }

    @Override
    public long nextLong(long bound) {
        return record(random.nextLong(bound));
    }

    @Override
    public long nextLong(long origin, long bound) {
        return record(random.nextLong(origin, bound));
    }

    @Override
    public double nextDouble() {
        return record(random.nextDouble());
    }

    @Override
    public boolean nextBoolean() {
        return record(random.nextBoolean());
    }

    private <N> N record(N value) {
        recorded.add(value);
        return value;
    }

    /**
     * Returns the Java expression of a {@link FakeRandom} that gives the numbers recorded so far, to paste in a test:
     * {@code new FakeRandom().add(5, 0).add(0.25).add(-3L)}.
     *
     * @return the expression
     */
    @Override
    public String toString() {
        var expression = new StringBuilder("new FakeRandom()");
        Class<?> type = null;
        for (var value : recorded) {
            if (value.getClass() == type) {
                expression.append(", ");
            } else {
                expression.append(type == null ? "" : ")").append(".add(");
                type = value.getClass();
            }
            expression.append(value).append(value instanceof Long ? "L" : "");
        }
        return expression.append(type == null ? "" : ")").toString();
    }
}
