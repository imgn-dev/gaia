package be.imgn.gaia.core.random;

import static be.imgn.gaia.core.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;

/**
 * A weighted {@link RandomSelector} whose elements and weights change: an element is added, removed or given another
 * weight, and selected, in a time that grows with the logarithm of the number of elements.
 *
 * <pre>{@code
 * var spawns = new MutableRandomSelector<Monster>();
 * spawns.put(goblin, 10);
 * spawns.put(dragon, 1);
 * spawns.next(random);          // a goblin, ten times out of eleven
 * spawns.put(dragon, 5);        // dragons get more common
 * spawns.remove(goblin);
 * }</pre>
 *
 * <p>For weights that don't change, {@link RandomSelector#weighted(java.util.Map)} selects faster. Elements are told
 * apart with {@code equals} and {@code hashCode}, as the keys of a map are.
 *
 * <p>Each selection calls {@link RandomGenerator#nextDouble()} once. Which element a given number selects isn't
 * specified.
 *
 * <p>This class is mutable, and isn't safe to use from several threads at once.
 *
 * @param <T> the type of the elements
 * @since 0.2.0
 */
public final class MutableRandomSelector<T> implements RandomSelector<T> {

    private static final int INITIAL_CAPACITY = 16;

    private final Map<T, Integer> indexes = new HashMap<>();

    // The capacity is a power of two, so that a selection halves its step down to one.
    private @Nullable Object[] elements = new Object[INITIAL_CAPACITY];
    private double[] weights = new double[INITIAL_CAPACITY];

    // A Fenwick tree of the weights: the entry at index i, from one, is the sum of the weights of the i & -i elements
    // that end at the element i - 1. Changing a weight or finding the element at a running total touches one entry
    // per bit of the capacity.
    private double[] tree = new double[INITIAL_CAPACITY + 1];
    private int size;
    private double total;
    private int changes;

    /** Creates a selector with no element. */
    public MutableRandomSelector() {}

    /**
     * Returns how many elements this selector has, whatever their weights.
     *
     * @return the number of elements
     */
    public int size() {
        return size;
    }

    /**
     * Returns whether this selector has no element.
     *
     * @return whether this selector is empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns whether this selector has an element.
     *
     * @param element the element to look for
     * @return whether {@code element} was put in this selector and not removed since
     */
    public boolean contains(@Nullable Object element) {
        return indexes.containsKey(element);
    }

    /**
     * Returns the weight of an element.
     *
     * @param element the element
     * @return its weight, or zero if this selector doesn't have it
     */
    public double weight(@Nullable Object element) {
        var index = indexes.get(element);
        return index == null ? 0 : weights[index];
    }

    /**
     * Returns the sum of the weights of the elements.
     *
     * @return the total weight
     */
    public double totalWeight() {
        return total;
    }

    /**
     * Adds an element with a weight, or changes the weight of an element that this selector already has. An element
     * whose weight is zero stays in this selector, and is never selected.
     *
     * @param element the element
     * @param weight its new weight
     * @throws IllegalArgumentException if {@code weight} is negative, infinite or not a number
     */
    public void put(T element, double weight) {
        requireNonNull(element, "element");
        checkArgument(weight >= 0 && Double.isFinite(weight), "Not a weight for %s: %s", element, weight);
        var index = indexes.get(element);
        if (index == null) {
            if (size == weights.length) {
                grow();
            }
            index = size++;
            elements[index] = element;
            indexes.put(element, index);
        }
        setWeight(index, weight);
    }

    /**
     * Removes an element.
     *
     * @param element the element to remove
     * @return whether this selector had the element
     */
    public boolean remove(@Nullable Object element) {
        var index = indexes.remove(element);
        if (index == null) {
            return false;
        }
        // The last element takes the place of the removed one.
        int last = size - 1;
        if (index != last) {
            @SuppressWarnings("unchecked")
            T moved = (T) requireNonNull(elements[last]);
            elements[index] = moved;
            indexes.put(moved, index);
            setWeight(index, weights[last]);
        }
        setWeight(last, 0);
        elements[last] = null;
        size = last;
        return true;
    }

    /** Removes every element. */
    public void clear() {
        indexes.clear();
        Arrays.fill(elements, 0, size, null);
        Arrays.fill(weights, 0);
        size = 0;
        rebuild();
    }

    /**
     * Selects an element with a chance proportional to its weight.
     *
     * @param random the source of randomness
     * @return the selected element
     * @throws IllegalStateException if no element has a positive weight
     */
    @Override
    public T next(RandomGenerator random) {
        requireNonNull(random, "random");
        if (!(total > 0)) {
            throw new IllegalStateException("No element has a positive weight");
        }
        double target = random.nextDouble() * total;
        // The first element at which the running total of the weights exceeds the target.
        int index = 0;
        for (int step = weights.length; step > 0; step >>= 1) {
            int next = index + step;
            if (next <= weights.length && tree[next] <= target) {
                index = next;
                target -= tree[next];
            }
        }
        if (index >= size || !(weights[index] > 0)) {
            index = nearestPositive(Math.min(index, size - 1));
        }
        @SuppressWarnings("unchecked")
        T selected = (T) requireNonNull(elements[index]);
        return selected;
    }

    /** The sums are rounded, so a target at the very end of an element may fall past it: takes one next to it. */
    private int nearestPositive(int index) {
        for (int i = index; i >= 0; i--) {
            if (weights[i] > 0) {
                return i;
            }
        }
        for (int i = index + 1; i < size; i++) {
            if (weights[i] > 0) {
                return i;
            }
        }
        // Only the rounding of the total made it positive.
        rebuild();
        throw new IllegalStateException("No element has a positive weight");
    }

    private void setWeight(int index, double weight) {
        double change = weight - weights[index];
        weights[index] = weight;
        for (int i = index + 1; i <= weights.length; i += i & -i) {
            tree[i] += change;
        }
        total += change;
        // Sums that are only ever adjusted drift away from the weights: now and then, they are computed again.
        if (++changes > weights.length) {
            rebuild();
        }
    }

    private void grow() {
        int capacity = 2 * weights.length;
        elements = Arrays.copyOf(elements, capacity);
        weights = Arrays.copyOf(weights, capacity);
        tree = new double[capacity + 1];
        rebuild();
    }

    private void rebuild() {
        total = 0;
        Arrays.fill(tree, 0);
        for (int i = 1; i <= weights.length; i++) {
            tree[i] += weights[i - 1];
            total += weights[i - 1];
            int parent = i + (i & -i);
            if (parent <= weights.length) {
                tree[parent] += tree[i];
            }
        }
        changes = 0;
    }
}
