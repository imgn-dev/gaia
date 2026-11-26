package be.imgn.gaia.core.random;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** The selectors that {@link RandomSelector} creates. */
final class Selectors {

    private Selectors() {}

    static <T> RandomSelector<T> uniform(List<T> elements) {
        checkArgument(!elements.isEmpty(), "No element to select from");
        if (elements.size() == 1) {
            return RandomSelector.constant(elements.getFirst());
        }
        var array = elements.toArray();
        @SuppressWarnings("unchecked")
        RandomSelector<T> selector = random -> (T) array[random.nextInt(array.length)];
        return selector;
    }

    /** Creates the selector of the elements that have a positive weight. */
    static <T> RandomSelector<T> weighted(List<T> elements, double[] weights) {
        var selected = new ArrayList<T>();
        var positive = new double[weights.length];
        double total = 0;
        boolean equal = true;
        for (int i = 0; i < weights.length; i++) {
            double weight = weights[i];
            checkArgument(weight >= 0 && Double.isFinite(weight), "Not a weight for %s: %s", elements.get(i), weight);
            if (weight > 0) {
                equal &= selected.isEmpty() || weight == positive[0];
                positive[selected.size()] = weight;
                selected.add(elements.get(i));
                total += weight;
            }
        }
        checkArgument(!selected.isEmpty(), "No element has a positive weight");
        checkArgument(Double.isFinite(total), "The sum of the weights is infinite");
        return equal ? uniform(List.copyOf(selected)) : AliasSelector.of(List.copyOf(selected), positive, total);
    }

    /**
     * A weighted selector that uses Walker's alias method, with a table built by Vose's algorithm.
     *
     * <p>The table has one slot per element. Each slot has the same chance, and is shared between its own element,
     * selected below a threshold, and another one, its alias. An element whose weight is below the average gives the
     * rest of its slot to a heavier one, which makes the heavier one lighter, until every slot is full.
     *
     * <p>A selection takes one random {@code long}. Multiplied by the number of slots, as a number of 128 bits, its
     * high half is the slot and its low half is how far into the slot it falls, so that no second number is needed. A
     * slot is off by less than its number over 2^64, which no amount of selections can show.
     */
    static final class AliasSelector<T> implements RandomSelector<T> {

        // The thresholds are fractions of this many bits, as many as a double has.
        private static final int FRACTION_BITS = 53;

        private final Object[] elements;

        // For each slot, its threshold then its alias, next to each other so that a selection reads them together.
        private final long[] table;

        private AliasSelector(Object[] elements, long[] table) {
            this.elements = elements;
            this.table = table;
        }

        static <T> AliasSelector<T> of(List<T> elements, double[] weights, double total) {
            int size = elements.size();
            // The share of a slot that each element still has to be given: one on average.
            var shares = new double[size];
            // Two stacks in one array: the elements below one slot from the start, the other ones from the end.
            var pending = new int[size];
            int light = 0;
            int heavy = size;
            for (int i = 0; i < size; i++) {
                shares[i] = weights[i] / total * size;
                if (shares[i] < 1) {
                    pending[light++] = i;
                } else {
                    pending[--heavy] = i;
                }
            }
            var table = new long[2 * size];
            while (light > 0 && heavy < size) {
                int lighter = pending[--light];
                int heavier = pending[heavy++];
                set(table, lighter, shares[lighter], heavier);
                shares[heavier] = shares[heavier] + shares[lighter] - 1;
                if (shares[heavier] < 1) {
                    pending[light++] = heavier;
                } else {
                    pending[--heavy] = heavier;
                }
            }
            // What is left is one slot each, but for rounding errors.
            while (light > 0) {
                int index = pending[--light];
                set(table, index, 1, index);
            }
            while (heavy < size) {
                int index = pending[heavy++];
                set(table, index, 1, index);
            }
            return new AliasSelector<>(elements.toArray(), table);
        }

        private static void set(long[] table, int slot, double threshold, int alias) {
            table[2 * slot] = (long) (threshold * (1L << FRACTION_BITS));
            table[2 * slot + 1] = alias;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T next(RandomGenerator random) {
            long number = random.nextLong();
            int size = elements.length;
            int slot = (int) Math.unsignedMultiplyHigh(number, size);
            long fraction = (number * size) >>> (Long.SIZE - FRACTION_BITS);
            long[] table = this.table;
            int index = fraction < table[2 * slot] ? slot : (int) table[2 * slot + 1];
            return (T) elements[index];
        }

        /** The chance of each element, as the table gives it. */
        double[] chances() {
            int size = elements.length;
            var chances = new double[size];
            for (int slot = 0; slot < size; slot++) {
                double threshold = (double) table[2 * slot] / (1L << FRACTION_BITS);
                chances[slot] += threshold / size;
                chances[(int) table[2 * slot + 1]] += (1 - threshold) / size;
            }
            return chances;
        }
    }
}
