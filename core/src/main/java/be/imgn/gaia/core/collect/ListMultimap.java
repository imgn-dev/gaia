package be.imgn.gaia.core.collect;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A multimap that keeps the values of each key in a list, in the order they were added, duplicates included.
 *
 * @param <K> the key type
 * @param <V> the value type
 * @since 0.1.2
 */
public interface ListMultimap<K, V> extends Multimap<K, V> {

    /**
     * Returns a live view of the values of a key, empty if the key is absent. Adding to the view adds entries to this
     * multimap, the key included if it was absent.
     *
     * @param key the key
     * @return the values of {@code key}, in the order they were added
     */
    @Override
    List<V> get(K key);

    /**
     * Returns a live view of this multimap as a map from each key to its values. The map and its lists support removal
     * and {@link List#set}, but not {@code put} nor adding values.
     *
     * @return this multimap as a map
     */
    @Override
    Map<K, List<V>> asMap();

    /**
     * Removes all the values of a key.
     *
     * @param key the key
     * @return the values removed, in an unmodifiable list, empty if the key was absent
     * @throws UnsupportedOperationException if this multimap is unmodifiable
     */
    @Override
    List<V> removeAll(@Nullable Object key);

    /**
     * Replaces all the values of a key. Replacing them with no values removes the key.
     *
     * @param key the key
     * @param values the new values of {@code key}, in order
     * @return the values replaced, in an unmodifiable list, empty if the key was absent
     * @throws UnsupportedOperationException if this multimap is unmodifiable
     */
    @Override
    List<V> replaceValues(K key, Iterable<? extends V> values);
}
