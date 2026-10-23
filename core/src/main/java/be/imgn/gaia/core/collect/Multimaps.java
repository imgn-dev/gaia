package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import java.util.stream.Collector;

/**
 * Static factories and collectors for unmodifiable multimaps that iterate over their keys in the order they first
 * appear, and over the values of a key in the order they are given.
 *
 * <p>They behave like the maps of {@link Maps}:
 *
 * <ul>
 *   <li>They are unmodifiable: every mutator method throws {@link UnsupportedOperationException}, even when the call
 *       would not change the multimap. That includes the methods of their views and their iterators.
 *   <li>They reject {@code null} keys and values, and querying them with {@code null}, as in {@code containsKey(null)},
 *       throws {@link NullPointerException}.
 *   <li>They are serializable if all their keys and values are.
 *   <li>They are value-based: do not rely on their identity.
 *   <li>{@code equals} and {@code hashCode} follow the {@link Multimap} contract, so a list multimap from this class
 *       equals any list multimap with the same keys and values in the same order, whatever the order of its keys.
 * </ul>
 *
 * @since 0.1.2
 */
public final class Multimaps {

    private Multimaps() {}

    /**
     * Returns an unmodifiable list multimap containing the entries of another multimap, in its iteration order.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param multimap the multimap whose entries to copy
     * @return a list multimap containing the entries of {@code multimap}
     */
    public static <K, V> ListMultimap<K, V> listMultimapCopy(Multimap<? extends K, ? extends V> multimap) {
        return ImmutableOrderedListMultimap.copyOf(multimap);
    }

    /**
     * Returns an unmodifiable set multimap containing the entries of another multimap, in its iteration order.
     * Duplicate entries are allowed: the first occurrence of each is kept.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param multimap the multimap whose entries to copy
     * @return a set multimap containing the entries of {@code multimap}
     */
    public static <K, V> SetMultimap<K, V> setMultimapCopy(Multimap<? extends K, ? extends V> multimap) {
        return ImmutableOrderedSetMultimap.copyOf(multimap);
    }

    /**
     * Returns a collector into an unmodifiable list multimap that iterates in encounter order: over its keys in the
     * order they are first encountered, and over the values of a key in the order they are encountered. The resulting
     * multimap behaves like the other multimaps of this class.
     *
     * @param <T> the type of the input elements
     * @param <K> the key type
     * @param <V> the value type
     * @param keyMapper the function that maps an element to its key
     * @param valueMapper the function that maps an element to its value
     * @return a collector into an ordered, unmodifiable list multimap
     */
    public static <T, K, V> Collector<T, ?, ListMultimap<K, V>> toListMultimap(
            Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends V> valueMapper) {
        requireNonNull(keyMapper, "keyMapper");
        requireNonNull(valueMapper, "valueMapper");
        return Collector.<T, ImmutableOrderedListMultimap.Builder<K, V>, ListMultimap<K, V>>of(
                ImmutableOrderedListMultimap.Builder::new,
                (builder, element) -> builder.put(
                        requireNonNull(keyMapper.apply(element), "key"),
                        requireNonNull(valueMapper.apply(element), "value")),
                ImmutableOrderedListMultimap.Builder::putAll,
                ImmutableOrderedListMultimap.Builder::build);
    }

    /**
     * Returns a collector into an unmodifiable set multimap that iterates in encounter order: over its keys in the
     * order they are first encountered, and over the values of a key in the order they are first encountered. Duplicate
     * entries are allowed: the first occurrence of each is kept. The resulting multimap behaves like the other
     * multimaps of this class.
     *
     * @param <T> the type of the input elements
     * @param <K> the key type
     * @param <V> the value type
     * @param keyMapper the function that maps an element to its key
     * @param valueMapper the function that maps an element to its value
     * @return a collector into an ordered, unmodifiable set multimap
     */
    public static <T, K, V> Collector<T, ?, SetMultimap<K, V>> toSetMultimap(
            Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends V> valueMapper) {
        requireNonNull(keyMapper, "keyMapper");
        requireNonNull(valueMapper, "valueMapper");
        return Collector.<T, ImmutableOrderedSetMultimap.Builder<K, V>, SetMultimap<K, V>>of(
                ImmutableOrderedSetMultimap.Builder::new,
                (builder, element) -> builder.put(
                        requireNonNull(keyMapper.apply(element), "key"),
                        requireNonNull(valueMapper.apply(element), "value")),
                ImmutableOrderedSetMultimap.Builder::putAll,
                ImmutableOrderedSetMultimap.Builder::build);
    }
}
