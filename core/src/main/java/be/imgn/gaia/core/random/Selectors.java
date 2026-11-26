package be.imgn.gaia.core.random;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

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
        return random -> elements.get(random.nextInt(elements.size()));
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
     */
    static final class AliasSelector<T> implements RandomSelector<T> {

        private final List<T> elements;
        private final double[] thresholds;
        private final int[] aliases;

        private AliasSelector(List<T> elements, double[] thresholds, int[] aliases) {
            this.elements = elements;
            this.thresholds = thresholds;
            this.aliases = aliases;
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
            var thresholds = new double[size];
            var aliases = new int[size];
            while (light > 0 && heavy < size) {
                int lighter = pending[--light];
                int heavier = pending[heavy++];
                thresholds[lighter] = shares[lighter];
                aliases[lighter] = heavier;
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
                thresholds[index] = 1;
                aliases[index] = index;
            }
            while (heavy < size) {
                int index = pending[heavy++];
                thresholds[index] = 1;
                aliases[index] = index;
            }
            return new AliasSelector<>(elements, thresholds, aliases);
        }

        @Override
        public T next(RandomGenerator random) {
            requireNonNull(random, "random");
            int slot = random.nextInt(thresholds.length);
            double threshold = thresholds[slot];
            return elements.get(threshold >= 1 || random.nextDouble() < threshold ? slot : aliases[slot]);
        }

        /** The chance of each element, as the table gives it. */
        double[] chances() {
            var chances = new double[thresholds.length];
            for (int slot = 0; slot < thresholds.length; slot++) {
                chances[slot] += thresholds[slot] / thresholds.length;
                chances[aliases[slot]] += (1 - thresholds[slot]) / thresholds.length;
            }
            return chances;
        }
    }
}
