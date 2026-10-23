package be.imgn.gaia.core.collect;

import static java.util.Objects.requireNonNull;

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A modifiable set multimap backed by a {@link HashMap} from each key to a {@link java.util.HashSet} of its values. Its
 * keys and values must have consistent {@code equals} and {@code hashCode} methods.
 *
 * <ul>
 *   <li>It iterates over its keys, and over the values of a key, in no particular order.
 *   <li>It rejects {@code null} keys and values, but querying it with {@code null}, as in {@code containsKey(null)}, is
 *       allowed.
 *   <li>Its views are live, and its iterators support {@link java.util.Iterator#remove()}. The collections of
 *       {@link #asMap()} do not support adding values: add them through {@link #get(Object)} or {@link #put}.
 *   <li>Its iterators fail fast, on a best-effort basis, when it changes while they iterate, outside of their own
 *       {@code remove()}.
 *   <li>It is serializable if all its keys and values are.
 *   <li>It is not thread-safe.
 * </ul>
 *
 * @param <K> the key type
 * @param <V> the value type
 * @since 0.1.2
 */
public final class HashSetMultimap<K, V> extends AbstractMapSetMultimap<K, V> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Map<K, Set<V>> map = new HashMap<>();
    private transient long total;

    private HashSetMultimap() {}

    /**
     * Returns a new, empty multimap.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @return a new, empty multimap
     */
    public static <K, V> HashSetMultimap<K, V> create() {
        return new HashSetMultimap<>();
    }

    /**
     * Returns a new multimap containing the entries of another multimap.
     *
     * @param <K> the key type
     * @param <V> the value type
     * @param multimap the multimap whose entries to add
     * @return a new multimap containing the entries of {@code multimap}
     */
    public static <K, V> HashSetMultimap<K, V> create(Multimap<? extends K, ? extends V> multimap) {
        requireNonNull(multimap, "multimap");
        var copy = new HashSetMultimap<K, V>();
        copy.putAll(multimap);
        return copy;
    }

    @Override
    Map<K, Set<V>> map() {
        return map;
    }

    @Override
    long total() {
        return total;
    }

    @Override
    void addToTotal(long delta) {
        total += delta;
    }

    @Override
    Set<V> newValues() {
        return new HashSet<>();
    }

    @Serial
    private Object writeReplace() {
        return new SerializedForm(this);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws InvalidObjectException {
        throw new InvalidObjectException("SerializedForm required");
    }

    private static final class SerializedForm extends SerializedMultimap {

        @Serial
        private static final long serialVersionUID = 1L;

        SerializedForm(HashSetMultimap<?, ?> multimap) {
            super(multimap);
        }

        @Serial
        private Object readResolve() {
            var multimap = HashSetMultimap.<Object, Object>create();
            forEachKey(multimap::putAll);
            return multimap;
        }
    }
}
