package be.imgn.gaia.core.collect;

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A multimap that keeps the values of each key in a set, without duplicates: adding an entry it already contains leaves
 * it unchanged.
 *
 * @param <K> the key type
 * @param <V> the value type
 * @since 0.1.2
 */
public interface SetMultimap<K, V> extends Multimap<K, V> {

    /**
     * Returns a live view of the values of a key, empty if the key is absent. Adding to the view adds entries to this
     * multimap, the key included if it was absent.
     *
     * @param key the key
     * @return the values of {@code key}
     */
    @Override
    Set<V> get(K key);

    /**
     * Returns a live view of the entries, grouped by key. The entries do not support {@link Map.Entry#setValue}.
     *
     * @return the entries of this multimap, which are distinct
     */
    @Override
    Set<Map.Entry<K, V>> entries();

    /**
     * Returns a live view of this multimap as a map from each key to its values. The map and its sets support removal,
     * but not {@code put} nor adding values.
     *
     * @return this multimap as a map
     */
    @Override
    Map<K, Set<V>> asMap();

    /**
     * Removes all the values of a key.
     *
     * @param key the key
     * @return the values removed, in an unmodifiable set, empty if the key was absent
     * @throws UnsupportedOperationException if this multimap is unmodifiable
     */
    @Override
    Set<V> removeAll(@Nullable Object key);

    /**
     * Replaces all the values of a key. Replacing them with no values removes the key.
     *
     * @param key the key
     * @param values the new values of {@code key}
     * @return the values replaced, in an unmodifiable set, empty if the key was absent
     * @throws UnsupportedOperationException if this multimap is unmodifiable
     */
    @Override
    Set<V> replaceValues(K key, Iterable<? extends V> values);
}
