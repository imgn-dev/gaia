package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Static factories and collectors for unmodifiable maps that iterate in the order their keys are given.
 *
 * <p>The {@code map} methods behave exactly like their {@link Map#of() Map.of} counterparts,
 * {@link #mapOfEntries(Map.Entry...)} like {@link Map#ofEntries(Map.Entry...) Map.ofEntries}, {@link #mapCopy(Map)}
 * like {@link Map#copyOf(Map) Map.copyOf}, and the {@code toMap} collectors like
 * {@link Collectors#toUnmodifiableMap(Function, Function) Collectors.toUnmodifiableMap}, except for iteration order and
 * one query. {@code Map.of} and {@code Map.copyOf} leave the order unspecified, and it changes from one run to the
 * next. These maps iterate over their keys, values and entries in the order the keys were given. And
 * {@code entrySet().contains(null)} throws {@link NullPointerException}, like every other query with {@code null},
 * where a {@code Map.of} map returns {@code false}. Otherwise they are the same:
 *
 * <ul>
 *   <li>They are unmodifiable: every mutator method throws {@link UnsupportedOperationException}, even when the call
 *       would not change the map. That includes the methods of their views and their iterators.
 *   <li>They reject {@code null} keys and values, and querying them with {@code null}, as in {@code get(null)} or
 *       {@code containsValue(null)}, throws {@link NullPointerException}.
 *   <li>The {@code map} methods and {@link #mapOfEntries(Map.Entry...)} reject duplicate keys with
 *       {@link IllegalArgumentException}, and the {@code toMap} collectors without a merger with
 *       {@link IllegalStateException}.
 *   <li>They are serializable if all their keys and values are.
 *   <li>They are value-based: do not rely on their identity.
 *   <li>{@code equals} and {@code hashCode} follow the {@link Map} contract, so a map from this class equals a
 *       {@code Map.of} map with the same entries, whatever their order.
 * </ul>
 *
 * @since 0.1.2
 */
public final class Maps {

    private Maps() {}

    /**
     * Returns an unmodifiable map containing zero mappings.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @return an empty map
     * @see Map#of()
     */
    public static <K, V> Map<K, V> map() {
        return ImmutableOrderedMap.of(List.of(), List.of());
    }

    /**
     * Returns an unmodifiable map containing a single mapping.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the mapping's key
     * @param v1 the mapping's value
     * @return a map containing the given mappings
     * @see Map#of(Object, Object)
     */
    public static <K, V> Map<K, V> map(K k1, V v1) {
        return ImmutableOrderedMap.of(Arrays.asList(k1), Arrays.asList(v1));
    }

    /**
     * Returns an unmodifiable map containing two mappings, iterating in the order given.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @return a map containing the given mappings
     * @throws IllegalArgumentException if there are any duplicate keys
     * @see Map#of(Object, Object, Object, Object)
     */
    public static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2) {
        return ImmutableOrderedMap.of(Arrays.asList(k1, k2), Arrays.asList(v1, v2));
    }

    /**
     * Returns an unmodifiable map containing three mappings, iterating in the order given.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @return a map containing the given mappings
     * @throws IllegalArgumentException if there are any duplicate keys
     * @see Map#of(Object, Object, Object, Object, Object, Object)
     */
    public static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3) {
        return ImmutableOrderedMap.of(Arrays.asList(k1, k2, k3), Arrays.asList(v1, v2, v3));
    }

    /**
     * Returns an unmodifiable map containing four mappings, iterating in the order given.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @return a map containing the given mappings
     * @throws IllegalArgumentException if there are any duplicate keys
     * @see Map#of(Object, Object, Object, Object, Object, Object, Object, Object)
     */
    // Mirrors Map.of, which goes up to ten mappings.
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        return ImmutableOrderedMap.of(Arrays.asList(k1, k2, k3, k4), Arrays.asList(v1, v2, v3, v4));
    }

    /**
     * Returns an unmodifiable map containing five mappings, iterating in the order given.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @param k5 the fifth mapping's key
     * @param v5 the fifth mapping's value
     * @return a map containing the given mappings
     * @throws IllegalArgumentException if there are any duplicate keys
     * @see Map#of(Object, Object, Object, Object, Object, Object, Object, Object, Object, Object)
     */
    // Mirrors Map.of, which goes up to ten mappings.
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        return ImmutableOrderedMap.of(Arrays.asList(k1, k2, k3, k4, k5), Arrays.asList(v1, v2, v3, v4, v5));
    }

    /**
     * Returns an unmodifiable map containing six mappings, iterating in the order given.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @param k5 the fifth mapping's key
     * @param v5 the fifth mapping's value
     * @param k6 the sixth mapping's key
     * @param v6 the sixth mapping's value
     * @return a map containing the given mappings
     * @throws IllegalArgumentException if there are any duplicate keys
     * @see Map#of(Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object)
     */
    // Mirrors Map.of, which goes up to ten mappings.
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <K, V> Map<K, V> map(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
        return ImmutableOrderedMap.of(Arrays.asList(k1, k2, k3, k4, k5, k6), Arrays.asList(v1, v2, v3, v4, v5, v6));
    }

    /**
     * Returns an unmodifiable map containing seven mappings, iterating in the order given.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @param k5 the fifth mapping's key
     * @param v5 the fifth mapping's value
     * @param k6 the sixth mapping's key
     * @param v6 the sixth mapping's value
     * @param k7 the seventh mapping's key
     * @param v7 the seventh mapping's value
     * @return a map containing the given mappings
     * @throws IllegalArgumentException if there are any duplicate keys
     * @see Map#of(Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object,
     *     Object, Object)
     */
    // Mirrors Map.of, which goes up to ten mappings.
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <K, V> Map<K, V> map(
            K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) {
        return ImmutableOrderedMap.of(
                Arrays.asList(k1, k2, k3, k4, k5, k6, k7), Arrays.asList(v1, v2, v3, v4, v5, v6, v7));
    }

    /**
     * Returns an unmodifiable map containing eight mappings, iterating in the order given.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @param k5 the fifth mapping's key
     * @param v5 the fifth mapping's value
     * @param k6 the sixth mapping's key
     * @param v6 the sixth mapping's value
     * @param k7 the seventh mapping's key
     * @param v7 the seventh mapping's value
     * @param k8 the eighth mapping's key
     * @param v8 the eighth mapping's value
     * @return a map containing the given mappings
     * @throws IllegalArgumentException if there are any duplicate keys
     * @see Map#of(Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object,
     *     Object, Object, Object, Object)
     */
    // Mirrors Map.of, which goes up to ten mappings.
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <K, V> Map<K, V> map(
            K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8) {
        return ImmutableOrderedMap.of(
                Arrays.asList(k1, k2, k3, k4, k5, k6, k7, k8), Arrays.asList(v1, v2, v3, v4, v5, v6, v7, v8));
    }

    /**
     * Returns an unmodifiable map containing nine mappings, iterating in the order given.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @param k5 the fifth mapping's key
     * @param v5 the fifth mapping's value
     * @param k6 the sixth mapping's key
     * @param v6 the sixth mapping's value
     * @param k7 the seventh mapping's key
     * @param v7 the seventh mapping's value
     * @param k8 the eighth mapping's key
     * @param v8 the eighth mapping's value
     * @param k9 the ninth mapping's key
     * @param v9 the ninth mapping's value
     * @return a map containing the given mappings
     * @throws IllegalArgumentException if there are any duplicate keys
     * @see Map#of(Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object,
     *     Object, Object, Object, Object, Object, Object)
     */
    // Mirrors Map.of, which goes up to ten mappings.
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <K, V> Map<K, V> map(
            K k1,
            V v1,
            K k2,
            V v2,
            K k3,
            V v3,
            K k4,
            V v4,
            K k5,
            V v5,
            K k6,
            V v6,
            K k7,
            V v7,
            K k8,
            V v8,
            K k9,
            V v9) {
        return ImmutableOrderedMap.of(
                Arrays.asList(k1, k2, k3, k4, k5, k6, k7, k8, k9), Arrays.asList(v1, v2, v3, v4, v5, v6, v7, v8, v9));
    }

    /**
     * Returns an unmodifiable map containing ten mappings, iterating in the order given.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @param k5 the fifth mapping's key
     * @param v5 the fifth mapping's value
     * @param k6 the sixth mapping's key
     * @param v6 the sixth mapping's value
     * @param k7 the seventh mapping's key
     * @param v7 the seventh mapping's value
     * @param k8 the eighth mapping's key
     * @param v8 the eighth mapping's value
     * @param k9 the ninth mapping's key
     * @param v9 the ninth mapping's value
     * @param k10 the tenth mapping's key
     * @param v10 the tenth mapping's value
     * @return a map containing the given mappings
     * @throws IllegalArgumentException if there are any duplicate keys
     * @see Map#of(Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object,
     *     Object, Object, Object, Object, Object, Object, Object, Object)
     */
    // Mirrors Map.of, which goes up to ten mappings.
    @SuppressWarnings("checkstyle:ParameterNumber")
    public static <K, V> Map<K, V> map(
            K k1,
            V v1,
            K k2,
            V v2,
            K k3,
            V v3,
            K k4,
            V v4,
            K k5,
            V v5,
            K k6,
            V v6,
            K k7,
            V v7,
            K k8,
            V v8,
            K k9,
            V v9,
            K k10,
            V v10) {
        return ImmutableOrderedMap.of(
                Arrays.asList(k1, k2, k3, k4, k5, k6, k7, k8, k9, k10),
                Arrays.asList(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10));
    }

    /**
     * Returns an unmodifiable map containing the keys and values of the given entries, iterating in the order given.
     * The entries themselves are not stored.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param entries the entries containing the keys and values
     * @return a map containing the given mappings
     * @throws IllegalArgumentException if there are any duplicate keys
     * @see Map#ofEntries(Map.Entry...)
     */
    @SafeVarargs
    public static <K, V> Map<K, V> mapOfEntries(Map.Entry<? extends K, ? extends V>... entries) {
        requireNonNull(entries, "entries");
        var keys = new ArrayList<K>(entries.length);
        var values = new ArrayList<V>(entries.length);
        for (var i = 0; i < entries.length; i++) {
            var entry = requireNonNull(entries[i], "entries[" + i + "]");
            keys.add(requireNonNull(entry.getKey(), "entries[" + i + "] has a null key"));
            values.add(requireNonNull(entry.getValue(), "entries[" + i + "] has a null value"));
        }
        return ImmutableOrderedMap.create(keys, values);
    }

    /**
     * Returns an unmodifiable map containing the mappings of {@code map}, iterating in the map's iteration order. If
     * {@code map} is already a map returned by this class, the copy shares its contents instead of copying them.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param map the map whose mappings to copy
     * @return a map containing the mappings of {@code map}
     * @throws IllegalArgumentException if {@code map} contains keys that are duplicates according to their
     *     {@code equals} method, as an {@link java.util.IdentityHashMap} can
     * @see Map#copyOf(Map)
     */
    public static <K, V> Map<K, V> mapCopy(Map<? extends K, ? extends V> map) {
        return ImmutableOrderedMap.copyOf(map);
    }

    /**
     * Returns a collector into an unmodifiable map that iterates in encounter order: the ordered counterpart of
     * {@link Collectors#toUnmodifiableMap(Function, Function)}. If two elements map to the same key, collecting throws
     * {@link IllegalStateException}. The resulting map behaves like the other maps of this class.
     *
     * @param <T> the type of the input elements
     * @param <K> the key type
     * @param <V> the value type
     * @param keyMapper the function that maps an element to its key
     * @param valueMapper the function that maps an element to its value
     * @return a collector into an ordered, unmodifiable map
     */
    public static <T, K, V> Collector<T, ?, Map<K, V>> toMap(
            Function<? super T, ? extends K> keyMapper, Function<? super T, ? extends V> valueMapper) {
        requireNonNull(keyMapper, "keyMapper");
        requireNonNull(valueMapper, "valueMapper");
        return Collector.<T, LinkedHashMap<K, V>, Map<K, V>>of(
                LinkedHashMap::new,
                (map, element) -> putUnique(
                        map,
                        requireNonNull(keyMapper.apply(element), "key"),
                        requireNonNull(valueMapper.apply(element), "value")),
                (left, right) -> {
                    right.forEach((key, value) -> putUnique(left, key, value));
                    return left;
                },
                ImmutableOrderedMap::copyOf);
    }

    /**
     * Returns a collector into an unmodifiable map that iterates in encounter order: the ordered counterpart of
     * {@link Collectors#toUnmodifiableMap(Function, Function, BinaryOperator)}. If several elements map to the same
     * key, their values are merged with {@code merger}, as {@link Map#merge(Object, Object,
     * java.util.function.BiFunction) Map.merge} does, and the key keeps the position of its first element. The
     * resulting map behaves like the other maps of this class.
     *
     * @param <T> the type of the input elements
     * @param <K> the key type
     * @param <V> the value type
     * @param keyMapper the function that maps an element to its key
     * @param valueMapper the function that maps an element to its value
     * @param merger the function that merges the values of the same key
     * @return a collector into an ordered, unmodifiable map
     */
    public static <T, K, V> Collector<T, ?, Map<K, V>> toMap(
            Function<? super T, ? extends K> keyMapper,
            Function<? super T, ? extends V> valueMapper,
            BinaryOperator<V> merger) {
        requireNonNull(keyMapper, "keyMapper");
        requireNonNull(valueMapper, "valueMapper");
        requireNonNull(merger, "merger");
        return Collector.<T, LinkedHashMap<K, V>, Map<K, V>>of(
                LinkedHashMap::new,
                (map, element) -> map.merge(
                        requireNonNull(keyMapper.apply(element), "key"),
                        requireNonNull(valueMapper.apply(element), "value"),
                        merger),
                (left, right) -> {
                    right.forEach((key, value) -> left.merge(key, value, merger));
                    return left;
                },
                ImmutableOrderedMap::copyOf);
    }

    private static <K, V> void putUnique(Map<K, V> map, K key, V value) {
        V previous = map.putIfAbsent(key, value);
        if (previous != null) {
            throw new IllegalStateException(
                    "duplicate key " + key + " (attempted merging values " + previous + " and " + value + ")");
        }
    }
}
