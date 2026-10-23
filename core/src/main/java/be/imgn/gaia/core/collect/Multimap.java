package be.imgn.gaia.core.collect;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;

/**
 * A collection that maps keys to values, like a {@link Map}, but where a key may have several values: its entries are
 * key-value pairs, and a key is in the multimap as long as it has a value.
 *
 * <pre>{@code
 * ListMultimap<String, Integer> scores = HashListMultimap.create();
 * scores.put("alice", 3);
 * scores.put("alice", 5);
 * scores.put("bob", 4);
 * scores.get("alice"); // [3, 5]
 * scores.size();       // 3
 * }</pre>
 *
 * <p>A {@link ListMultimap} keeps the values of a key in a list, in the order they were added, duplicates included. A
 * {@link SetMultimap} keeps them in a set, without duplicates.
 *
 * <p>Its views are live: {@link #get(Object)}, {@link #keySet()}, {@link #values()}, {@link #entries()} and
 * {@link #asMap()} reflect the changes made to the multimap, and removing from them removes from the multimap. Only
 * {@code get(key)} supports adding. A key whose last value is removed, through the multimap or a view, leaves the
 * multimap.
 *
 * <p>{@link #size()} is the number of entries, up to {@link Integer#MAX_VALUE}. No multimap of this library contains
 * {@code null} keys or values.
 *
 * @param <K> the key type
 * @param <V> the value type
 * @since 0.1.2
 */
public interface Multimap<K, V> {

    /**
     * Returns the number of entries: the key-value pairs, not the distinct keys.
     *
     * @return the number of entries, or {@link Integer#MAX_VALUE} if there are more
     */
    int size();

    /**
     * Returns whether this multimap has no entries.
     *
     * @return {@code true} if this multimap is empty
     */
    boolean isEmpty();

    /**
     * Returns whether a key has at least one value.
     *
     * @param key the key to look for
     * @return {@code true} if this multimap contains {@code key}
     */
    boolean containsKey(@Nullable Object key);

    /**
     * Returns whether a value belongs to at least one key.
     *
     * @param value the value to look for
     * @return {@code true} if this multimap contains {@code value}
     */
    boolean containsValue(@Nullable Object value);

    /**
     * Returns whether a key has a value.
     *
     * @param key the key to look for
     * @param value the value to look for
     * @return {@code true} if this multimap contains an entry with {@code key} and {@code value}
     */
    boolean containsEntry(@Nullable Object key, @Nullable Object value);

    /**
     * Returns a live view of the values of a key, empty if the key is absent. Adding to the view adds entries to this
     * multimap, the key included if it was absent.
     *
     * @param key the key
     * @return the values of {@code key}
     */
    Collection<V> get(K key);

    /**
     * Returns a live view of the distinct keys. Removing a key removes all its values.
     *
     * @return the keys of this multimap
     */
    Set<K> keySet();

    /**
     * Returns a live view of the values of all keys, each value as many times as it occurs, grouped by key.
     *
     * @return the values of this multimap
     */
    Collection<V> values();

    /**
     * Returns a live view of the entries, grouped by key. The entries do not support {@link Map.Entry#setValue}.
     *
     * @return the entries of this multimap
     */
    Collection<Map.Entry<K, V>> entries();

    /**
     * Returns a live view of this multimap as a map from each key to its values. The map and its collections support
     * removal, but not {@code put} nor adding values.
     *
     * @return this multimap as a map
     */
    Map<K, ? extends Collection<V>> asMap();

    /**
     * Calls an action with the key and value of each entry, grouped by key.
     *
     * @param action the action to call
     */
    void forEach(BiConsumer<? super K, ? super V> action);

    /**
     * Adds an entry.
     *
     * @param key the key
     * @param value the value
     * @return {@code true} if this multimap changed, which a {@link SetMultimap} does not if it already contains the
     *     entry
     * @throws UnsupportedOperationException if this multimap is unmodifiable
     */
    boolean put(K key, V value);

    /**
     * Adds an entry for each of the given values, with the same key.
     *
     * @param key the key
     * @param values the values
     * @return {@code true} if this multimap changed
     * @throws UnsupportedOperationException if this multimap is unmodifiable
     */
    boolean putAll(K key, Iterable<? extends V> values);

    /**
     * Adds the entries of another multimap.
     *
     * @param multimap the multimap whose entries to add
     * @return {@code true} if this multimap changed
     * @throws UnsupportedOperationException if this multimap is unmodifiable
     */
    boolean putAll(Multimap<? extends K, ? extends V> multimap);

    /**
     * Removes an entry. A {@link ListMultimap} removes its first occurrence.
     *
     * @param key the key
     * @param value the value
     * @return {@code true} if this multimap changed
     * @throws UnsupportedOperationException if this multimap is unmodifiable
     */
    boolean remove(@Nullable Object key, @Nullable Object value);

    /**
     * Removes all the values of a key.
     *
     * @param key the key
     * @return the values removed, in an unmodifiable collection, empty if the key was absent
     * @throws UnsupportedOperationException if this multimap is unmodifiable
     */
    Collection<V> removeAll(@Nullable Object key);

    /**
     * Replaces all the values of a key. Replacing them with no values removes the key.
     *
     * @param key the key
     * @param values the new values of {@code key}
     * @return the values replaced, in an unmodifiable collection, empty if the key was absent
     * @throws UnsupportedOperationException if this multimap is unmodifiable
     */
    Collection<V> replaceValues(K key, Iterable<? extends V> values);

    /**
     * Removes all entries.
     *
     * @throws UnsupportedOperationException if this multimap is unmodifiable
     */
    void clear();

    /**
     * Returns whether another object is a multimap with the same keys and values. Two multimaps are equal when their
     * {@link #asMap()} views are, so a {@link ListMultimap} equals another one with the same values in the same order,
     * and a {@link SetMultimap} another one with the same values in any order. A list multimap never equals a set
     * multimap, unless both are empty.
     *
     * @param o the object to compare with
     * @return {@code true} if {@code o} is an equal multimap
     */
    @Override
    boolean equals(@Nullable Object o);

    /**
     * Returns the hash code of {@link #asMap()}.
     *
     * @return the hash code of this multimap
     */
    @Override
    int hashCode();
}
