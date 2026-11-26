package be.imgn.gaia.core.random;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

/**
 * Selects an element at random, each time it's asked to, with the source of randomness it's given.
 *
 * <pre>{@code
 * var rarity = RandomSelector.weighted(Maps.map("common", 70, "rare", 25, "legendary", 5));
 * rarity.next(random);                         // "common", 70% of the time
 * rarity.stream(random).limit(10).toList();
 *
 * var monster = RandomSelector.weighted(bestiary, Monster::frequency);
 * var card = RandomSelector.uniform(deck);
 * }</pre>
 *
 * <p>A weighted selection takes the same time whatever the number of elements: the weights are turned once into a table
 * of as many slots as there are elements, each shared between at most two of them, and a selection takes a slot, then
 * one of its two elements, with a single call to {@link RandomGenerator#nextLong()}.
 *
 * <p>Selectors are combined into other selectors. {@link #flatten(RandomSelector)} selects a selector, then an element
 * from it, which is how a table of elements refers to other tables:
 *
 * <pre>{@code
 * var gems = RandomSelector.weighted(Maps.map(ruby, 3, diamond, 1));
 * var weapons = RandomSelector.uniform(List.of(sword, axe, bow));
 * var chest = RandomSelector.flatten(
 *         RandomSelector.weighted(Maps.map(gems, 30, weapons, 10, RandomSelector.constant(gold), 60)));
 * }</pre>
 *
 * <p>This is a functional interface: any function of a {@link RandomGenerator} is a selector, such as {@code random ->
 * random.nextInt(1, 7)}. The selectors that this interface creates are immutable, keep a copy of their elements, and
 * may be shared between threads if the source of randomness given to them may.
 *
 * @param <T> the type of the elements
 * @since 0.2.0
 * @see RandomTable
 */
@FunctionalInterface
public interface RandomSelector<T> {

    /**
     * Selects an element.
     *
     * @param random the source of randomness
     * @return the selected element
     */
    T next(RandomGenerator random);

    /**
     * Returns an infinite stream of elements, each selected when the stream asks for it.
     *
     * @param random the source of randomness
     * @return an infinite stream of selected elements
     */
    default Stream<T> stream(RandomGenerator random) {
        requireNonNull(random, "random");
        return Stream.generate(() -> next(random));
    }

    /**
     * Returns a selector of what a function returns for the elements that this selector selects.
     *
     * @param <R> the type of the new elements
     * @param mapper the function to apply to each selected element
     * @return a selector of the mapped elements
     */
    default <R> RandomSelector<R> map(Function<? super T, ? extends R> mapper) {
        requireNonNull(mapper, "mapper");
        return random -> mapper.apply(next(random));
    }

    /**
     * Returns a selector that always selects the same element, and uses no randomness.
     *
     * @param <T> the type of the element
     * @param element the element
     * @return a selector of {@code element}
     */
    static <T> RandomSelector<T> constant(T element) {
        requireNonNull(element, "element");
        return random -> {
            requireNonNull(random, "random");
            return element;
        };
    }

    /**
     * Returns a selector that selects each element of a collection with the same chance. An element that occurs several
     * times in the collection has as many chances to be selected.
     *
     * @param <T> the type of the elements
     * @param elements the elements to select from
     * @return a selector of the elements
     * @throws IllegalArgumentException if {@code elements} is empty
     */
    static <T> RandomSelector<T> uniform(Collection<? extends T> elements) {
        return Selectors.uniform(List.<T>copyOf(elements));
    }

    /**
     * Returns a selector that selects each element of a collection with a chance proportional to its weight. An element
     * whose weight is zero is never selected.
     *
     * @param <T> the type of the elements
     * @param elements the elements to select from
     * @param weight the function that gives the weight of an element
     * @return a selector of the elements
     * @throws IllegalArgumentException if a weight is negative, infinite or not a number, if their sum is infinite, or
     *     if no element has a positive weight
     */
    static <T> RandomSelector<T> weighted(Collection<? extends T> elements, ToDoubleFunction<? super T> weight) {
        requireNonNull(weight, "weight");
        List<T> copy = List.copyOf(elements);
        return Selectors.weighted(copy, copy.stream().mapToDouble(weight).toArray());
    }

    /**
     * Returns a selector that selects each key of a map with a chance proportional to its value. A key whose weight is
     * zero is never selected.
     *
     * <p>Which element a given source of randomness selects depends on the order of the map: for selections that are
     * the same from one run to the next, with a seeded source, use a map with a defined order.
     *
     * @param <T> the type of the elements
     * @param weights the weights of the elements to select from
     * @return a selector of the elements
     * @throws IllegalArgumentException if a weight is negative, infinite or not a number, if their sum is infinite, or
     *     if no element has a positive weight
     */
    static <T> RandomSelector<T> weighted(Map<? extends T, ? extends Number> weights) {
        var elements = new ArrayList<T>(weights.size());
        var values = new double[weights.size()];
        for (var entry : weights.entrySet()) {
            values[elements.size()] = entry.getValue().doubleValue();
            elements.add(requireNonNull(entry.getKey(), "weights has a null key"));
        }
        return Selectors.weighted(elements, values);
    }

    /**
     * Returns a selector that selects a selector, then an element from it.
     *
     * @param <T> the type of the elements
     * @param selectors the selector of selectors
     * @return a selector of the elements of the selected selectors
     */
    static <T> RandomSelector<T> flatten(RandomSelector<? extends RandomSelector<? extends T>> selectors) {
        requireNonNull(selectors, "selectors");
        return random -> selectors.next(random).next(random);
    }
}
