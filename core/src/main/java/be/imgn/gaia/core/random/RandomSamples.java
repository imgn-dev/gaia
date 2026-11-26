package be.imgn.gaia.core.random;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;
import java.util.stream.Collector;

/**
 * Samples: several different elements taken at random among other ones, without taking any twice.
 *
 * <pre>{@code
 * RandomSamples.sample(players, 3, random);                         // three different players
 * RandomSamples.weightedSample(prizes, Prize::tickets, 3, random);  // three different prizes, by weight
 * lines.collect(RandomSamples.toSample(100, random));               // 100 lines of a stream of any length
 * }</pre>
 *
 * <p>A sample has fewer elements than asked for when there aren't enough to take. An element that occurs several times
 * may be taken as many times.
 *
 * @since 0.2.0
 */
public final class RandomSamples {

    private RandomSamples() {}

    /**
     * Takes elements of a collection, each set of elements with the same chance.
     *
     * <p>It calls {@link RandomGenerator#nextInt(int)} once per element taken, with the number of elements that are
     * left to take from.
     *
     * @param <T> the type of the elements
     * @param elements the elements to take from
     * @param count how many elements to take
     * @param random the source of randomness
     * @return the elements taken, in a random order: {@code count} of them, or all of them if there are fewer
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public static <T> List<T> sample(Collection<? extends T> elements, int count, RandomGenerator random) {
        requireNonNull(random, "random");
        checkArgument(count >= 0, "count must not be negative: %d", count);
        return RandomBag.<T>of(elements).draw(Math.min(count, elements.size()), random);
    }

    /**
     * Takes elements of a collection one after the other, each time with chances proportional to the weights of the
     * elements that are left. An element whose weight is zero is never taken.
     *
     * <p>It calls {@link RandomGenerator#nextDouble()} once per element whose weight is positive, in the iteration
     * order of the collection.
     *
     * @param <T> the type of the elements
     * @param elements the elements to take from
     * @param weight the function that gives the weight of an element
     * @param count how many elements to take
     * @param random the source of randomness
     * @return the elements taken, in the order they were taken: {@code count} of them, or all those with a positive
     *     weight if there are fewer
     * @throws IllegalArgumentException if {@code count} is negative, or if a weight is negative, infinite or not a
     *     number
     */
    public static <T> List<T> weightedSample(
            Collection<? extends T> elements, ToDoubleFunction<? super T> weight, int count, RandomGenerator random) {
        requireNonNull(weight, "weight");
        requireNonNull(random, "random");
        checkArgument(count >= 0, "count must not be negative: %d", count);
        // Each element gets the time at which it would be taken if elements were taken at a rate equal to their
        // weight: the first ones are a weighted sample (Efraimidis and Spirakis). The queue has the latest on top.
        record Timed<E>(E element, double time) {}
        var latest = new PriorityQueue<Timed<T>>(
                Comparator.comparingDouble((Timed<T> timed) -> timed.time).reversed());
        for (T element : List.<T>copyOf(elements)) {
            double value = weight.applyAsDouble(element);
            checkArgument(value >= 0 && Double.isFinite(value), "Not a weight for %s: %s", element, value);
            if (value > 0 && count > 0) {
                double time = -Math.log(1 - random.nextDouble()) / value;
                if (latest.size() < count) {
                    latest.add(new Timed<>(element, time));
                } else if (time < latest.element().time) {
                    latest.remove();
                    latest.add(new Timed<>(element, time));
                }
            }
        }
        var sample = new ArrayList<T>(latest.size());
        while (!latest.isEmpty()) {
            sample.add(latest.remove().element);
        }
        return List.copyOf(sample.reversed());
    }

    /**
     * Returns a collector that takes elements of a stream, each set of elements with the same chance, without knowing
     * how many elements the stream has and while keeping only the sample in memory.
     *
     * <p>Past the first {@code count} elements, it uses the source of randomness only for the elements it takes, which
     * get rarer as the stream goes on. Which methods it calls isn't specified.
     *
     * @param <T> the type of the elements
     * @param count how many elements to take
     * @param random the source of randomness
     * @return a collector of the elements taken, in no particular order: {@code count} of them, or all of them if the
     *     stream has fewer. It doesn't support parallel streams.
     * @throws IllegalArgumentException if {@code count} is negative
     */
    public static <T> Collector<T, ?, List<T>> toSample(int count, RandomGenerator random) {
        requireNonNull(random, "random");
        checkArgument(count >= 0, "count must not be negative: %d", count);
        return Collector.<T, Reservoir<T>, List<T>>of(
                () -> new Reservoir<>(count, random),
                Reservoir::add,
                (left, right) -> {
                    throw new UnsupportedOperationException("A sample cannot be collected in parallel");
                },
                reservoir -> List.copyOf(reservoir.sample));
    }

    /**
     * A sample of a stream that is still going on (algorithm L, by Li): instead of deciding for each element whether it
     * replaces one of the sample, it draws how many elements to skip before the next one that does.
     */
    private static final class Reservoir<T> {

        private final int count;
        private final RandomGenerator random;
        private final List<T> sample = new ArrayList<>();

        // The largest of the random keys that the elements of the sample would have, if each element had one.
        private double threshold = 1;
        private long toSkip;

        Reservoir(int count, RandomGenerator random) {
            this.count = count;
            this.random = random;
        }

        void add(T element) {
            requireNonNull(element, "element");
            if (count == 0) {
                return;
            }
            if (sample.size() < count) {
                sample.add(element);
                if (sample.size() == count) {
                    next();
                }
            } else if (toSkip > 0) {
                toSkip--;
            } else {
                sample.set(random.nextInt(count), element);
                next();
            }
        }

        private void next() {
            threshold *= Math.exp(Math.log(positive()) / count);
            double skipped = Math.floor(Math.log(positive()) / Math.log1p(-threshold));
            // Past the range of a long when the threshold underflows: no element is ever taken again.
            toSkip = (long) skipped;
        }

        private double positive() {
            return 1 - random.nextDouble();
        }
    }
}
